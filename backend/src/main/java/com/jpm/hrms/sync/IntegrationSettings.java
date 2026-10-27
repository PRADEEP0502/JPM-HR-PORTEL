package com.jpm.hrms.sync;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


import java.time.Instant;

/** Single-row, admin-editable integration settings. Credentials are deliberately NOT stored here. */
@Document("integration_settings")
public class IntegrationSettings implements SequencedEntity {
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

    /** Settings documents keep their fixed id; every save refreshes updatedAt. */
    @Override
    public void assignId(Long id) {
        this.id = id;
    }

    @Override
    public void onCreate() {
        updatedAt = Instant.now();
    }

    @Override
    public void onUpdate() {
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
