package com.jpm.hrms.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sync_runs")
public class SyncRun {

    public enum Status { RUNNING, SUCCESS, PARTIAL, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant startedAt;
    private Instant finishedAt;
    @Column(nullable = false)
    private String triggeredBy;
    @Enumerated(EnumType.STRING)
    private Status status;
    private int rowsRead;
    private int createdCount;
    private int duplicateCount;
    private int skippedCount;
    private int warningCount;
    /** Newline-separated row-level messages (skips / warnings), capped in size. */
    private String messages;
    private String errorMessage;

    protected SyncRun() {
    }

    public SyncRun(String triggeredBy) {
        this.triggeredBy = triggeredBy;
        this.startedAt = Instant.now();
        this.status = Status.RUNNING;
    }

    public Long getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getTriggeredBy() { return triggeredBy; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getRowsRead() { return rowsRead; }
    public void setRowsRead(int rowsRead) { this.rowsRead = rowsRead; }
    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }
    public String getMessages() { return messages; }
    public void setMessages(String messages) { this.messages = messages; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
