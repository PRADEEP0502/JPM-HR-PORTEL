package com.jpm.hrms.sync;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


import java.time.Instant;

@Document("sync_ignored_rows")
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
