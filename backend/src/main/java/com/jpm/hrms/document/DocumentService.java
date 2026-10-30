package com.jpm.hrms.document;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateDtos.DocumentResponse;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.config.AppProperties;
import com.jpm.hrms.document.CandidateDocument.DocType;
import com.jpm.hrms.document.CandidateDocument.ScanStatus;
import com.jpm.hrms.document.CandidateDocument.Storage;
import com.jpm.hrms.document.scan.MalwareScanner;
import com.jpm.hrms.document.storage.FileStorage;
import com.jpm.hrms.document.storage.FileStorages;
import com.jpm.hrms.sync.GoogleApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Candidate documents: validated (extension allow-list + real file signature), malware-scanned, stored under a
 * random key in private storage, and served only to signed-in users after a permission check by the caller.
 */
@Service
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    /** Every type the server knows how to verify; app.files.allowed-extensions selects which are accepted. */
    private static final Map<String, String> KNOWN = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");
    /** Types a browser may show inline in the resume viewer. Everything else is always downloaded. */
    private static final Set<String> VIEWABLE = Set.of("application/pdf", "image/jpeg", "image/png");

    private final CandidateDocumentRepository docs;
    private final CandidateRepository candidates;
    private final GoogleApiClient google;
    private final DocumentCrypto crypto;
    private final FileStorages storages;
    private final MalwareScanner scanner;
    private final AuditService audit;
    private final List<String> allowed;
    private final boolean failClosed;

    public DocumentService(CandidateDocumentRepository docs, CandidateRepository candidates, GoogleApiClient google,
                           DocumentCrypto crypto, FileStorages storages, MalwareScanner scanner, AuditService audit,
                           AppProperties props) {
        this.docs = docs;
        this.candidates = candidates;
        this.google = google;
        this.crypto = crypto;
        this.storages = storages;
        this.scanner = scanner;
        this.audit = audit;
        this.allowed = props.files().allowedExtensions().stream().map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(KNOWN::containsKey).toList();
        this.failClosed = props.files().malwareScan().failClosed();
    }

    /** Human-readable list of accepted types, e.g. "PDF, JPG, JPEG, PNG". */
    public String acceptedTypes() {
        return String.join(", ", allowed.stream().map(e -> e.toUpperCase(Locale.ROOT)).toList());
    }

    /**
     * Stores an uploaded file. When an encryption key is configured every file is encrypted at rest;
     * Aadhaar copies additionally require the key and the candidate's recorded consent.
     */
    public DocumentResponse upload(Long candidateId, DocType type, MultipartFile file, boolean consent, String actor) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to upload");
        try {
            return store(candidateId, type, file.getOriginalFilename(), file.getBytes(), consent, actor);
        } catch (IOException e) {
            log.error("Could not read uploaded file for candidate {}: {}", candidateId, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the uploaded file");
        }
    }

    /** Same checks as {@link #upload}, for files that arrive as bytes (Google Form submissions). */
    public DocumentResponse store(Long candidateId, DocType type, String filename, byte[] bytes, boolean consent, String actor) {
        Candidate c = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Candidate"));
        if (bytes == null || bytes.length == 0) throw ApiException.badRequest("Choose a file to upload");
        if (type == DocType.AADHAAR) {
            if (!consent) throw ApiException.badRequest("Confirm that the candidate consented to sharing their Aadhaar copy");
            if (!crypto.isConfigured()) {
                throw ApiException.badRequest("Aadhaar uploads are disabled until DOCUMENT_ENCRYPTION_KEY is configured on the server");
            }
        }
        // Only the base name is used, for display; the storage key is random (no user-controlled paths).
        String base = filename == null ? "" : filename.replace('\\', '/').substring(filename.replace('\\', '/').lastIndexOf('/') + 1);
        String original = base.isBlank() ? "document" : base;
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!allowed.contains(ext)) throw ApiException.badRequest("Only " + acceptedTypes() + " files are allowed");
        String contentType = KNOWN.get(ext);
        try {
            if (!signatureMatches(ext, Arrays.copyOf(bytes, Math.min(bytes.length, 8)))) {
                throw ApiException.badRequest("The file content does not match its ." + ext + " extension");
            }
            ScanStatus scanStatus = scan(c, original, bytes, actor);
            boolean encrypt = crypto.isConfigured();
            FileStorage storage = storages.forNewFiles();
            String key = storage.store(String.valueOf(c.getId()), encrypt ? "enc" : ext, encrypt ? crypto.encrypt(bytes) : bytes);
            String safeName = original.replaceAll("[\\r\\n\"\\\\/]", "_");
            CandidateDocument d = new CandidateDocument(c, type, storage.kind(), safeName, contentType, (long) bytes.length, key, actor);
            d.setEncrypted(encrypt);
            d.setScanStatus(scanStatus);
            docs.save(d);
            audit.record("DOCUMENT_UPLOADED", "CANDIDATE", c.getId(), label(type) + " uploaded: " + safeName
                    + (type == DocType.AADHAAR ? " (candidate consent confirmed, encrypted)" : "")
                    + (scanStatus == ScanStatus.CLEAN ? " - malware scan clean" : ""));
            return DocumentResponse.from(d);
        } catch (IOException e) {
            log.error("Could not store uploaded file for candidate {}: {}", c.getId(), e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the uploaded file");
        }
    }

    private ScanStatus scan(Candidate c, String name, byte[] bytes, String actor) {
        MalwareScanner.Verdict v = scanner.scan(bytes);
        return switch (v.outcome()) {
            case CLEAN -> ScanStatus.CLEAN;
            case NOT_SCANNED -> ScanStatus.NOT_SCANNED;
            case INFECTED -> {
                audit.recordAs(actor, "UPLOAD_REJECTED_MALWARE", "CANDIDATE", c.getId(), "Upload rejected: " + name + " (" + v.detail() + ")");
                throw ApiException.badRequest("This file was rejected because the malware scanner flagged it");
            }
            case ERROR -> {
                log.warn("Malware scan failed: {}", v.detail());
                if (failClosed) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Uploads are paused: the malware scanner is not available. Try again later.");
                }
                yield ScanStatus.NOT_SCANNED;
            }
        };
    }

    /** {@code inline} = shown in the resume viewer (PDF / images only); otherwise always a download. */
    public ResponseEntity<InputStreamResource> download(Long documentId, boolean inline) {
        CandidateDocument d = docs.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
        return stream(d, inline);
    }

    public ResponseEntity<InputStreamResource> downloadLatestResume(Long candidateId) {
        return latestResume(candidateId, false);
    }

    public ResponseEntity<InputStreamResource> latestResume(Long candidateId, boolean inline) {
        if (!candidates.existsById(candidateId)) throw ApiException.notFound("Candidate");
        CandidateDocument d = docs.findFirstByCandidateIdAndDocTypeOrderByCreatedAtDesc(candidateId, DocType.RESUME)
                .orElseThrow(() -> ApiException.notFound("Resume for this candidate"));
        return stream(d, inline);
    }

    /** Removes a document (e.g. an Aadhaar copy once verification is complete). Drive files are only unlinked. */
    public void delete(Long documentId) {
        CandidateDocument d = docs.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
        FileStorage storage = storages.of(d.getStorage());
        if (storage != null) {
            try {
                storage.delete(d.getStorageRef());
            } catch (IOException e) {
                log.warn("Could not delete stored file for document {}", d.getId());
            }
        }
        docs.delete(d);
        audit.record("DOCUMENT_DELETED", "CANDIDATE", d.getCandidateId(), label(d.getDocType()) + " deleted: "
                + d.getFileName() + (d.getStorage() == Storage.DRIVE ? " (link removed; file remains in Google Drive)" : ""));
    }

    private ResponseEntity<InputStreamResource> stream(CandidateDocument d, boolean inline) {
        InputStream body;
        String name = d.getFileName();
        Long size;
        FileStorage storage = storages.of(d.getStorage());
        if (storage != null) {
            try {
                if (!storage.exists(d.getStorageRef())) throw ApiException.notFound("Stored file");
                byte[] stored = storage.read(d.getStorageRef());
                byte[] plain = d.isEncrypted() ? crypto.decrypt(stored) : stored;
                body = new ByteArrayInputStream(plain);
                size = (long) plain.length;
            } catch (IOException e) {
                throw ApiException.notFound("Stored file");
            } catch (IllegalStateException e) {
                log.error("Could not decrypt document {}: {}", d.getId(), e.getMessage());
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "This document could not be decrypted. Contact an administrator.");
            }
        } else {
            try {
                GoogleApiClient.DriveFile meta = google.driveMetadata(d.getStorageRef());
                if (name == null || name.isBlank() || name.startsWith("Form upload")) name = meta.name();
                size = meta.size();
                body = google.driveDownload(d.getStorageRef());
            } catch (GoogleApiClient.GoogleApiException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not fetch the file from Google Drive: " + e.getMessage());
            }
        }
        String type = contentTypeOf(d, name);
        boolean showInline = inline && VIEWABLE.contains(type);
        audit.record(showInline ? "DOCUMENT_VIEWED" : "DOCUMENT_DOWNLOADED", "CANDIDATE", d.getCandidateId(),
                label(d.getDocType()) + (showInline ? " viewed: " : " downloaded: ") + name);
        String fileName = name == null ? "document" : name;
        var builder = ResponseEntity.ok()
                // Downloads are never rendered by the browser; the viewer only gets PDF / images with their real type.
                .contentType(showInline ? MediaType.parseMediaType(type) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, (showInline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox");
        if (size != null) builder.contentLength(size);
        return builder.body(new InputStreamResource(body));
    }

    private static String contentTypeOf(CandidateDocument d, String name) {
        if (d.getContentType() != null) return d.getContentType();
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String ext = n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : "";
        return KNOWN.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private static String label(DocType t) {
        return switch (t) {
            case RESUME -> "Resume";
            case AADHAAR -> "Aadhaar copy";
            case CERTIFICATE -> "Certificate";
            case OTHER -> "Document";
        };
    }

    private static boolean signatureMatches(String ext, byte[] h) {
        return switch (ext) {
            case "pdf" -> startsWith(h, '%', 'P', 'D', 'F');
            case "png" -> startsWith(h, 0x89, 'P', 'N', 'G');
            case "jpg", "jpeg" -> startsWith(h, 0xFF, 0xD8, 0xFF);
            case "docx" -> startsWith(h, 'P', 'K', 0x03, 0x04);
            case "doc" -> startsWith(h, 0xD0, 0xCF, 0x11, 0xE0);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] h, int... sig) {
        if (h.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if ((h[i] & 0xFF) != sig[i]) return false;
        return true;
    }
}
