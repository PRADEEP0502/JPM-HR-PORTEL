package com.jpm.hrms.document;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateDtos.DocumentResponse;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.config.AppProperties;
import com.jpm.hrms.document.CandidateDocument.DocType;
import com.jpm.hrms.document.CandidateDocument.Storage;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Map<String, String> ALLOWED = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");

    private final CandidateDocumentRepository docs;
    private final CandidateRepository candidates;
    private final GoogleApiClient google;
    private final DocumentCrypto crypto;
    private final AuditService audit;
    private final Path root;

    public DocumentService(CandidateDocumentRepository docs, CandidateRepository candidates, GoogleApiClient google,
                           DocumentCrypto crypto, AuditService audit, AppProperties props) {
        this.docs = docs;
        this.candidates = candidates;
        this.google = google;
        this.crypto = crypto;
        this.audit = audit;
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    /**
     * Stores an uploaded file. When an encryption key is configured every file is encrypted at rest;
     * Aadhaar copies additionally require the key and the candidate's recorded consent.
     */
    public DocumentResponse upload(Long candidateId, DocType type, MultipartFile file, boolean consent, String actor) {
        Candidate c = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Candidate"));
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to upload");
        if (type == DocType.AADHAAR) {
            if (!consent) throw ApiException.badRequest("Confirm that the candidate consented to sharing their Aadhaar copy");
            if (!crypto.isConfigured()) {
                throw ApiException.badRequest("Aadhaar uploads are disabled until DOCUMENT_ENCRYPTION_KEY is configured on the server");
            }
        }
        String original = file.getOriginalFilename() == null ? "document" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String contentType = ALLOWED.get(ext);
        if (contentType == null) throw ApiException.badRequest("Only PDF, DOC, DOCX, JPG and PNG files are allowed");
        try {
            byte[] bytes = file.getBytes();
            if (!signatureMatches(ext, Arrays.copyOf(bytes, Math.min(bytes.length, 8)))) {
                throw ApiException.badRequest("The file content does not match its ." + ext + " extension");
            }
            boolean encrypt = crypto.isConfigured();
            Path dir = root.resolve(String.valueOf(c.getId()));
            Files.createDirectories(dir);
            String stored = UUID.randomUUID() + (encrypt ? ".enc" : "." + ext);
            Path target = dir.resolve(stored).normalize();
            if (!target.startsWith(root)) throw ApiException.badRequest("Invalid file name");
            Files.write(target, encrypt ? crypto.encrypt(bytes) : bytes);

            String safeName = original.replaceAll("[\\r\\n\"\\\\/]", "_");
            CandidateDocument d = new CandidateDocument(c, type, Storage.LOCAL, safeName, contentType,
                    (long) bytes.length, c.getId() + "/" + stored, actor);
            d.setEncrypted(encrypt);
            docs.save(d);
            audit.record("DOCUMENT_UPLOADED", "CANDIDATE", c.getId(), label(type) + " uploaded: " + safeName
                    + (type == DocType.AADHAAR ? " (candidate consent confirmed, encrypted)" : ""));
            return DocumentResponse.from(d);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the uploaded file");
        }
    }

    public ResponseEntity<InputStreamResource> download(Long documentId) {
        CandidateDocument d = docs.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
        return stream(d);
    }

    public ResponseEntity<InputStreamResource> downloadLatestResume(Long candidateId) {
        if (!candidates.existsById(candidateId)) throw ApiException.notFound("Candidate");
        CandidateDocument d = docs.findFirstByCandidateIdAndDocTypeOrderByCreatedAtDesc(candidateId, DocType.RESUME)
                .orElseThrow(() -> ApiException.notFound("Resume for this candidate"));
        return stream(d);
    }

    /** Removes a document (e.g. an Aadhaar copy once verification is complete). Drive files are only unlinked. */
    public void delete(Long documentId) {
        CandidateDocument d = docs.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
        if (d.getStorage() == Storage.LOCAL) {
            Path file = root.resolve(d.getStorageRef()).normalize();
            if (file.startsWith(root)) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("Could not delete stored file for document {}", d.getId());
                }
            }
        }
        docs.delete(d);
        audit.record("DOCUMENT_DELETED", "CANDIDATE", d.getCandidateId(), label(d.getDocType()) + " deleted: "
                + d.getFileName() + (d.getStorage() == Storage.DRIVE ? " (link removed; file remains in Google Drive)" : ""));
    }

    private ResponseEntity<InputStreamResource> stream(CandidateDocument d) {
        InputStream body;
        String name = d.getFileName();
        Long size;
        if (d.getStorage() == Storage.LOCAL) {
            Path file = root.resolve(d.getStorageRef()).normalize();
            if (!file.startsWith(root) || !Files.isReadable(file)) throw ApiException.notFound("Stored file");
            try {
                if (d.isEncrypted()) {
                    byte[] plain = crypto.decrypt(Files.readAllBytes(file));
                    body = new ByteArrayInputStream(plain);
                    size = (long) plain.length;
                } else {
                    body = Files.newInputStream(file);
                    size = Files.size(file);
                }
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
        audit.record("DOCUMENT_DOWNLOADED", "CANDIDATE", d.getCandidateId(), label(d.getDocType()) + " downloaded: " + name);
        var builder = ResponseEntity.ok()
                // Always served as a download (never rendered inline) to avoid content-sniffing issues.
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name == null ? "document" : name, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (size != null) builder.contentLength(size);
        return builder.body(new InputStreamResource(body));
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
