package com.jpm.hrms.document;

import com.jpm.hrms.candidate.Candidate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "candidate_documents")
public class CandidateDocument {

    /** AADHAAR is a copy of the identity card only (no number is stored or verified); always stored encrypted. */
    public enum DocType { RESUME, AADHAAR, CERTIFICATE, OTHER }

    /** LOCAL = file uploaded to this server; DRIVE = Google Drive file uploaded through the Google Form. */
    public enum Storage { LOCAL, DRIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocType docType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Storage storage;

    private String fileName;
    private String contentType;
    private Long sizeBytes;
    /** Server-side file name (LOCAL) or Drive file id (DRIVE). Never sent to the browser. */
    @Column(nullable = false)
    private String storageRef;
    private String uploadedBy;
    /** True when the LOCAL file on disk is AES-GCM encrypted (see DocumentCrypto). */
    private boolean encrypted;
    private Instant createdAt;

    protected CandidateDocument() {
    }

    public CandidateDocument(Candidate candidate, DocType docType, Storage storage, String fileName,
                             String contentType, Long sizeBytes, String storageRef, String uploadedBy) {
        this.candidate = candidate;
        this.docType = docType;
        this.storage = storage;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageRef = storageRef;
        this.uploadedBy = uploadedBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Candidate getCandidate() { return candidate; }
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
