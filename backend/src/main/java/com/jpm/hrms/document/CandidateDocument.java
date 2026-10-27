package com.jpm.hrms.document;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.jpm.hrms.candidate.Candidate;

import java.time.Instant;

@Document("candidate_documents")
public class CandidateDocument implements SequencedEntity {

    /** AADHAAR is a copy of the identity card only (no number is stored or verified); always stored encrypted. */
    public enum DocType { RESUME, AADHAAR, CERTIFICATE, OTHER }

    /** LOCAL = file uploaded to this server; DRIVE = Google Drive file uploaded through the Google Form. */
    public enum Storage { LOCAL, DRIVE }

    @Id
    private Long id;

    private Long candidateId;

    private DocType docType;

    private Storage storage;

    private String fileName;
    private String contentType;
    private Long sizeBytes;
    /** Server-side file name (LOCAL) or Drive file id (DRIVE). Never sent to the browser. */
    private String storageRef;
    private String uploadedBy;
    /** True when the LOCAL file on disk is AES-GCM encrypted (see DocumentCrypto). */
    private boolean encrypted;
    private Instant createdAt;

    protected CandidateDocument() {
    }

    public CandidateDocument(Candidate candidate, DocType docType, Storage storage, String fileName,
                             String contentType, Long sizeBytes, String storageRef, String uploadedBy) {
        this.candidateId = candidate.getId();
        this.docType = docType;
        this.storage = storage;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageRef = storageRef;
        this.uploadedBy = uploadedBy;
    }

    @Override
    public void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    @Override
    public void assignId(Long id) { this.id = id; }
    public Long getCandidateId() { return candidateId; }
    public DocType getDocType() { return docType; }
    public Storage getStorage() { return storage; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getStorageRef() { return storageRef; }
    public String getUploadedBy() { return uploadedBy; }
    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
    public Instant getCreatedAt() { return createdAt; }
}
