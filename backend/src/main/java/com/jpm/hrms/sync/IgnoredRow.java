package com.jpm.hrms.sync;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sync_ignored_rows")
public class IgnoredRow {
    @Id
    private String rowKey;
    private String reason;
    private Instant firstSeenAt;

    protected IgnoredRow() {
    }

    public IgnoredRow(String rowKey, String reason) {
        this.rowKey = rowKey;
        this.reason = reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.firstSeenAt = Instant.now();
    }

    public String getRowKey() { return rowKey; }
    public String getReason() { return reason; }
}
