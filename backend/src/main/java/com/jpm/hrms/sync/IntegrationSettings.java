package com.jpm.hrms.sync;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Single-row, admin-editable integration settings. Credentials are deliberately NOT stored here. */
@Entity
@Table(name = "integration_settings")
public class IntegrationSettings {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;
    private String spreadsheetId;
    private String sheetRange;
    private String formUrl;
    private boolean autoSyncEnabled;
    private int syncIntervalMinutes = 15;
    /** JSON object: field key → exact sheet header, overriding automatic header detection. */
    private String columnMapping;
    private String updatedBy;
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSpreadsheetId() { return spreadsheetId; }
    public void setSpreadsheetId(String spreadsheetId) { this.spreadsheetId = spreadsheetId; }
    public String getSheetRange() { return sheetRange; }
    public void setSheetRange(String sheetRange) { this.sheetRange = sheetRange; }
    public String getFormUrl() { return formUrl; }
    public void setFormUrl(String formUrl) { this.formUrl = formUrl; }
    public boolean isAutoSyncEnabled() { return autoSyncEnabled; }
    public void setAutoSyncEnabled(boolean autoSyncEnabled) { this.autoSyncEnabled = autoSyncEnabled; }
    public int getSyncIntervalMinutes() { return syncIntervalMinutes; }
    public void setSyncIntervalMinutes(int syncIntervalMinutes) { this.syncIntervalMinutes = syncIntervalMinutes; }
    public String getColumnMapping() { return columnMapping; }
    public void setColumnMapping(String columnMapping) { this.columnMapping = columnMapping; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
