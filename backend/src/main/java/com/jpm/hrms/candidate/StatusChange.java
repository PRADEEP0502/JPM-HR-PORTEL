package com.jpm.hrms.candidate;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One recruitment status change: who changed it, when, from what to what. Never edited or deleted. */
@Document("recruitment_status_history")
public class StatusChange implements SequencedEntity {
    @Id
    private Long id;
    private Long candidateId;
    private ApplicationStatus fromStatus;
    private ApplicationStatus toStatus;
    private String changedBy;
    private Instant changedAt;
    private String note;

    protected StatusChange() {
    }

    public StatusChange(Long candidateId, ApplicationStatus fromStatus, ApplicationStatus toStatus, String changedBy, String note) {
        this.candidateId = candidateId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
        this.changedAt = Instant.now();
    }

    public Long getId() { return id; }
    @Override
    public void assignId(Long id) { this.id = id; }
    public Long getCandidateId() { return candidateId; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public String getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public String getNote() { return note; }
}
