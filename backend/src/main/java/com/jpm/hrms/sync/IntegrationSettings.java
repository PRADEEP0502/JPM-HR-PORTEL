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
    /** Shared key the Google Form's Apps Script sends with every submission (Settings shows it to admins). */
    private String formPushKey;
    /** Last response the form script sent, and what happened to it (shown in Settings to check the setup). */
    private Instant lastFormSubmissionAt;
    private String lastFormSubmissionResult;
    /** Last request that came with a wrong form key. */
    private Instant lastRejectedKeyAt;
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
    public String getFormPushKey() { return formPushKey; }
    public void setFormPushKey(String formPushKey) { this.formPushKey = formPushKey; }
    public Instant getLastFormSubmissionAt() { return lastFormSubmissionAt; }
    public String getLastFormSubmissionResult() { return lastFormSubmissionResult; }
    public void recordFormSubmission(Instant at, String result) { lastFormSubmissionAt = at; lastFormSubmissionResult = result; }
    public Instant getLastRejectedKeyAt() { return lastRejectedKeyAt; }
    public void setLastRejectedKeyAt(Instant lastRejectedKeyAt) { this.lastRejectedKeyAt = lastRejectedKeyAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
