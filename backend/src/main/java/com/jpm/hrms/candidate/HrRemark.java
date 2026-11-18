package com.jpm.hrms.candidate;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A note HR keeps on a candidate: a phone call, an interview, a reference number or anything else.
 * Older notes have no type and count as GENERAL.
 */
@Document("hr_remarks")
public class HrRemark implements SequencedEntity {
    public enum NoteType { GENERAL, CALL, INTERVIEW, REFERENCE }

    @Id
    private Long id;

    private Long candidateId;

    private String author;

    private String remark;

    private NoteType type;

    /** Optional reference number (offer letter, employee ID, ticket ...); required for REFERENCE notes. */
    private String referenceNo;

    private Instant createdAt;

    private Instant editedAt;

    private String editedBy;

    protected HrRemark() {
    }

    public HrRemark(Candidate candidate, String author, String remark) {
        this(candidate, author, remark, NoteType.GENERAL, null);
    }

    public HrRemark(Candidate candidate, String author, String remark, NoteType type, String referenceNo) {
        this.candidateId = candidate.getId();
        this.author = author;
        this.remark = remark;
        this.type = type;
        this.referenceNo = referenceNo;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    @Override
    public void assignId(Long id) { this.id = id; }
    public Long getCandidateId() { return candidateId; }
    public String getAuthor() { return author; }
    public String getRemark() { return remark; }
    public NoteType getType() { return type == null ? NoteType.GENERAL : type; }
    public String getReferenceNo() { return referenceNo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
    public String getEditedBy() { return editedBy; }

    public void edit(String remark, NoteType type, String referenceNo, String editor) {
        this.remark = remark;
        this.type = type;
        this.referenceNo = referenceNo;
        this.editedAt = Instant.now();
        this.editedBy = editor;
    }
}
