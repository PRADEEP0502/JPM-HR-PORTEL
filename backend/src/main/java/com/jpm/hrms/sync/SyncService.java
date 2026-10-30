package com.jpm.hrms.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.candidate.CandidateService;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.config.AppProperties;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import com.jpm.hrms.document.CandidateDocument;
import com.jpm.hrms.document.CandidateDocument.DocType;
import com.jpm.hrms.document.CandidateDocumentRepository;
import com.jpm.hrms.document.DocumentService;
import com.jpm.hrms.sync.SheetRowMapper.ColumnResolution;
import com.jpm.hrms.sync.SheetRowMapper.ParsedRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulls Google Form responses from the configured Google Sheet and imports new candidates.
 * Idempotent: every row gets a stable key, so re-running never creates duplicates.
 */
@Service
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    public static final String SYNC_ACTOR = "google-sheets-sync";
    public static final String FORM_ACTOR = "google-form";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_MESSAGES = 200;

    public record SettingsRequest(@Size(max = 200) String spreadsheetId,
                                  @Size(max = 200) String sheetRange,
                                  @Size(max = 500) String formUrl,
                                  boolean autoSyncEnabled,
                                  @Min(5) @Max(1440) int syncIntervalMinutes,
                                  Map<String, String> columnMapping) {
    }

    public record FieldInfo(String key, String label, boolean required) {
    }

    public record SettingsResponse(String spreadsheetId, String sheetRange, String formUrl, boolean autoSyncEnabled,
                                   int syncIntervalMinutes, Map<String, String> columnMapping, List<FieldInfo> fields,
                                   boolean credentialsConfigured, String serviceAccountEmail, String formPushKey,
                                   String updatedBy, Instant updatedAt) {
    }

    /** One file answered in the form; data is the file content in base64. field = RESUME, AADHAAR, CERTIFICATE or OTHER. */
    public record FormFile(@NotBlank String field, @Size(max = 300) String name, @NotBlank String data) {
    }

    /** A Google Form response sent by the form's Apps Script: the response sheet's header row and the new row. */
    public record FormSubmission(@NotBlank String key, @NotEmpty @Size(max = 200) List<String> headers,
                                 @NotEmpty @Size(max = 200) List<Object> values, @Size(max = 10) List<@Valid FormFile> files) {
    }

    /** status = CREATED, ALREADY_IMPORTED, DUPLICATE or SKIPPED. */
    public record FormSubmissionResult(String status, String candidateCode, List<String> messages) {
    }

    public record SyncRunResponse(Long id, Instant startedAt, Instant finishedAt, String triggeredBy, SyncRun.Status status,
                                  int rowsRead, int createdCount, int duplicateCount, int skippedCount, int warningCount,
                                  List<String> messages, String errorMessage) {
        static SyncRunResponse from(SyncRun r) {
            return new SyncRunResponse(r.getId(), r.getStartedAt(), r.getFinishedAt(), r.getTriggeredBy(), r.getStatus(),
                    r.getRowsRead(), r.getCreatedCount(), r.getDuplicateCount(), r.getSkippedCount(), r.getWarningCount(),
                    r.getMessages() == null || r.getMessages().isBlank() ? List.of() : List.of(r.getMessages().split("\n")),
                    r.getErrorMessage());
        }
    }

    public record SyncStatus(boolean configured, boolean credentialsConfigured, boolean spreadsheetConfigured,
                             boolean autoSyncEnabled, int syncIntervalMinutes, boolean running, String formUrl,
                             Instant lastSyncAt, SyncRun.Status lastStatus, Instant lastSuccessAt, String lastError) {
    }

    public record TestResult(boolean ok, String spreadsheetTitle, List<String> headers, Map<String, String> detectedMapping,
                             List<String> unmappedHeaders, List<String> problems, int dataRows) {
    }

    private final GoogleApiClient google;
    private final IntegrationSettingsRepository settingsRepo;
    private final SyncRunRepository runs;
    private final IgnoredRowRepository ignored;
    private final CandidateRepository candidates;
    private final CandidateDocumentRepository documents;
    private final DocumentService documentService;
    private final DepartmentRepository departments;
    private final AuditService audit;
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ZoneId zone;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SyncService(GoogleApiClient google, IntegrationSettingsRepository settingsRepo, SyncRunRepository runs,
                       IgnoredRowRepository ignored, CandidateRepository candidates, CandidateDocumentRepository documents,
                       DocumentService documentService,
                       DepartmentRepository departments, AuditService audit, AppProperties props, ObjectMapper mapper,
                       Clock clock, ZoneId businessZone) {
        this.google = google;
        this.settingsRepo = settingsRepo;
        this.runs = runs;
        this.ignored = ignored;
        this.candidates = candidates;
        this.documents = documents;
        this.documentService = documentService;
        this.departments = departments;
        this.audit = audit;
        this.props = props;
        this.mapper = mapper;
        this.clock = clock;
        this.zone = businessZone;
    }

    // ---------------------------------------------------------------- settings

    /** Loads the settings row, creating it from environment defaults on first use. */
    public IntegrationSettings settings() {
        return settingsRepo.findById(IntegrationSettings.SINGLETON_ID).orElseGet(() -> {
            IntegrationSettings s = new IntegrationSettings();
            s.setSpreadsheetId(blankToNull(props.google().spreadsheetId()));
            s.setSheetRange(StringUtils.hasText(props.google().sheetRange()) ? props.google().sheetRange() : "Form Responses 1");
            s.setFormUrl(blankToNull(props.google().formUrl()));
            s.setUpdatedBy(AuditService.SYSTEM_ACTOR);
            return settingsRepo.save(s);
        });
    }

    public SettingsResponse getSettings() {
        return toResponse(settings());
    }

    public SettingsResponse updateSettings(SettingsRequest req, String actor) {
        String sheetId = blankToNull(req.spreadsheetId());
        if (sheetId != null && !sheetId.matches("^[A-Za-z0-9_-]{20,100}$")) {
            throw ApiException.badRequest("Spreadsheet ID looks invalid. Copy the ID between /d/ and /edit in the sheet URL.");
        }
        String formUrl = blankToNull(req.formUrl());
        if (formUrl != null && !formUrl.matches("^https://(docs\\.google\\.com/forms/|forms\\.gle/)\\S+$")) {
            throw ApiException.badRequest("Form URL must be a https://docs.google.com/forms/... or https://forms.gle/... link");
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        if (req.columnMapping() != null) {
            req.columnMapping().forEach((k, v) -> {
                try {
                    SheetField.valueOf(k);
                } catch (IllegalArgumentException e) {
                    throw ApiException.badRequest("Unknown mapping field: " + k);
                }
                if (StringUtils.hasText(v)) mapping.put(k, v.trim());
            });
        }
        IntegrationSettings s = settings();
        s.setSpreadsheetId(sheetId);
        s.setSheetRange(StringUtils.hasText(req.sheetRange()) ? req.sheetRange().trim() : "Form Responses 1");
        s.setFormUrl(formUrl);
        s.setAutoSyncEnabled(req.autoSyncEnabled());
        s.setSyncIntervalMinutes(req.syncIntervalMinutes());
        s.setColumnMapping(mapping.isEmpty() ? null : writeJson(mapping));
        s.setUpdatedBy(actor);
        settingsRepo.save(s);
        audit.record("INTEGRATION_SETTINGS_UPDATED", "SETTINGS", s.getId(),
                "Sheet " + (sheetId == null ? "(not set)" : mask(sheetId)) + ", range \"" + s.getSheetRange() + "\", auto-sync "
                        + (s.isAutoSyncEnabled() ? "every " + s.getSyncIntervalMinutes() + " min" : "off")
                        + ", " + mapping.size() + " custom column mapping(s)");
        return toResponse(s);
    }

    public SyncStatus status() {
        IntegrationSettings s = settings();
        var last = runs.findTopByOrderByStartedAtDesc();
        var lastOk = runs.findTopByStatusInOrderByStartedAtDesc(List.of(SyncRun.Status.SUCCESS, SyncRun.Status.PARTIAL));
        boolean creds = google.isConfigured();
        boolean sheet = StringUtils.hasText(s.getSpreadsheetId());
        return new SyncStatus(creds && sheet, creds, sheet, s.isAutoSyncEnabled(), s.getSyncIntervalMinutes(), running.get(),
                s.getFormUrl(),
                last.map(r -> r.getFinishedAt() != null ? r.getFinishedAt() : r.getStartedAt()).orElse(null),
                last.map(SyncRun::getStatus).orElse(null),
                lastOk.map(r -> r.getFinishedAt() != null ? r.getFinishedAt() : r.getStartedAt()).orElse(null),
                last.filter(r -> r.getStatus() == SyncRun.Status.FAILED).map(SyncRun::getErrorMessage).orElse(null));
    }

    public boolean isConfigured() {
        return google.isConfigured() && StringUtils.hasText(settings().getSpreadsheetId());
    }

    // ---------------------------------------------------------------- test connection

    public TestResult testConnection() {
        IntegrationSettings s = settings();
        List<String> problems = new ArrayList<>();
        if (!google.isConfigured()) problems.add("Service-account credentials are not configured on the server (GOOGLE_SERVICE_ACCOUNT_FILE).");
        if (!StringUtils.hasText(s.getSpreadsheetId())) problems.add("Spreadsheet ID is not set.");
        if (!problems.isEmpty()) return new TestResult(false, null, List.of(), Map.of(), List.of(), problems, 0);
        try {
            String title = google.spreadsheetTitle(s.getSpreadsheetId());
            List<List<Object>> values = google.readValues(s.getSpreadsheetId(), s.getSheetRange());
            if (values.isEmpty()) {
                return new TestResult(false, title, List.of(), Map.of(), List.of(), List.of("The sheet range is empty - no header row found."), 0);
            }
            ColumnResolution res = SheetRowMapper.resolveColumns(values.get(0), readMapping(s));
            Map<String, String> detected = new LinkedHashMap<>();
            List<String> headers = values.get(0).stream().map(String::valueOf).toList();
            res.columns().forEach((f, idx) -> detected.put(f.name(), headers.get(idx)));
            return new TestResult(res.problems().isEmpty(), title, headers, detected, res.unmappedHeaders(), res.problems(),
                    values.size() - 1);
        } catch (GoogleApiClient.GoogleApiException e) {
            return new TestResult(false, null, List.of(), Map.of(), List.of(), List.of(e.getMessage()), 0);
        }
    }

    // ---------------------------------------------------------------- sync

    public SyncRunResponse runNow(String triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            throw ApiException.conflict("A synchronization is already in progress");
        }
        try {
            return SyncRunResponse.from(doSync(triggeredBy));
        } finally {
            running.set(false);
        }
    }

    /** Called by the scheduler; skips silently when not due, not configured, or already running. */
    public void runIfDue() {
        IntegrationSettings s = settings();
        if (s == null || !s.isAutoSyncEnabled() || !isConfigured()) return;
        var last = runs.findTopByOrderByStartedAtDesc();
        Instant now = clock.instant();
        if (last.isPresent() && last.get().getStartedAt().plus(Duration.ofMinutes(s.getSyncIntervalMinutes())).isAfter(now)) return;
        if (!running.compareAndSet(false, true)) return;
        try {
            doSync("scheduler");
        } finally {
            running.set(false);
        }
    }

    private SyncRun doSync(String triggeredBy) {
        SyncRun run = runs.save(new SyncRun(triggeredBy));
        List<String> messages = new ArrayList<>();
        try {
            IntegrationSettings s = settings();
            if (!google.isConfigured()) throw new SyncFailure("Service-account credentials are not configured on the server.");
            if (s == null || !StringUtils.hasText(s.getSpreadsheetId())) throw new SyncFailure("Spreadsheet ID is not configured.");

            List<List<Object>> values = google.readValues(s.getSpreadsheetId(), s.getSheetRange());
            if (values.isEmpty()) throw new SyncFailure("The sheet range is empty - no header row found.");
            ColumnResolution res = SheetRowMapper.resolveColumns(values.get(0), readMapping(s));
            if (!res.problems().isEmpty()) throw new SyncFailure(String.join(" ", res.problems()));

            Map<String, Department> deptByName = new HashMap<>();
            departments.findAll().stream().filter(Department::isActive)
                    .forEach(d -> deptByName.put(d.getName().toLowerCase(Locale.ROOT), d));
            LocalDate today = LocalDate.now(clock);

            int created = 0, duplicates = 0, skipped = 0, warnings = 0;
            run.setRowsRead(values.size() - 1);
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row.stream().allMatch(v -> v == null || v.toString().isBlank())) continue;
                ParsedRow p = SheetRowMapper.parse(row, i + 1, res.columns(), res.positionColumns(), zone, today);
                if (candidates.existsBySourceRowKey(p.rowKey()) || ignored.existsById(p.rowKey())) continue;

                if (!p.isValid()) {
                    skipped++;
                    String reason = "Row " + p.rowNumber() + ": skipped - " + String.join("; ", p.errors());
                    addMessage(messages, reason);
                    ignored.save(new IgnoredRow(p.rowKey(), reason));
                    continue;
                }
                String dup = duplicateReason(p);
                if (dup != null) {
                    duplicates++;
                    String reason = "Row " + p.rowNumber() + ": duplicate of existing candidate (" + dup + ")";
                    addMessage(messages, reason);
                    ignored.save(new IgnoredRow(p.rowKey(), reason));
                    continue;
                }
                List<String> rowWarnings = new ArrayList<>(p.warnings());
                Department dept = p.department() == null ? null : deptByName.get(p.department().toLowerCase(Locale.ROOT));
                if (p.department() != null && dept == null) rowWarnings.add("department \"" + p.department() + "\" is not configured");
                try {
                    Candidate c = importRow(p, dept, rowWarnings, true);
                    created++;
                    if (!rowWarnings.isEmpty()) {
                        warnings++;
                        addMessage(messages, "Row " + p.rowNumber() + " (" + (c == null ? "" : c.getCandidateCode()) + "): imported with warnings - "
                                + String.join("; ", rowWarnings));
                    }
                } catch (RuntimeException e) {
                    log.warn("Failed to import sheet row {}", p.rowNumber(), e);
                    skipped++;
                    addMessage(messages, "Row " + p.rowNumber() + ": could not be saved (" + e.getClass().getSimpleName() + ")");
                }
            }
            run.setCreatedCount(created);
            run.setDuplicateCount(duplicates);
            run.setSkippedCount(skipped);
            run.setWarningCount(warnings);
            run.setStatus(skipped > 0 || duplicates > 0 || warnings > 0 ? SyncRun.Status.PARTIAL : SyncRun.Status.SUCCESS);
        } catch (SyncFailure | GoogleApiClient.GoogleApiException e) {
            run.setStatus(SyncRun.Status.FAILED);
            run.setErrorMessage(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Synchronization failed", e);
            run.setStatus(SyncRun.Status.FAILED);
            run.setErrorMessage("Unexpected error during synchronization. See server logs.");
        }
        run.setFinishedAt(Instant.now());
        run.setMessages(messages.isEmpty() ? null : String.join("\n", messages));
        runs.save(run);
        audit.recordAs(triggeredBy, "SYNC_RUN", "SYNC", run.getId(), "Google Sheets sync " + run.getStatus()
                + ": " + run.getCreatedCount() + " new, " + run.getDuplicateCount() + " duplicate, " + run.getSkippedCount() + " skipped"
                + (run.getErrorMessage() != null ? " - " + run.getErrorMessage() : ""));
        return run;
    }

    // ---------------------------------------------------------------- form push

    /** Shows the key once it exists; the first call creates it. */
    public SettingsResponse newPushKey(String actor) {
        IntegrationSettings s = settings();
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        s.setFormPushKey(Base64.getUrlEncoder().withoutPadding().encodeToString(random));
        s.setUpdatedBy(actor);
        settingsRepo.save(s);
        audit.record("FORM_PUSH_KEY_CREATED", "SETTINGS", s.getId(), "New Google Form key created; the old key stops working");
        return toResponse(s);
    }

    /**
     * Imports one Google Form response pushed by the form's Apps Script the moment a candidate submits. Works
     * without the service account: uploaded files arrive in the request. Same row key, duplicate and validation
     * rules as the sheet sync, so sending a response twice (or syncing the sheet too) never creates a duplicate.
     */
    public synchronized FormSubmissionResult importSubmission(FormSubmission sub) {
        IntegrationSettings s = settings();
        String expected = s.getFormPushKey();
        if (expected == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sub.key().getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong form key. Copy it again from Settings → Google Sheets integration.");
        }
        ColumnResolution res = SheetRowMapper.resolveColumns(new ArrayList<>(sub.headers()), readMapping(s));
        if (!res.problems().isEmpty()) throw ApiException.badRequest(String.join(" ", res.problems()));
        ParsedRow p = SheetRowMapper.parse(sub.values(), 0, res.columns(), res.positionColumns(), zone, LocalDate.now(clock));

        if (candidates.existsBySourceRowKey(p.rowKey()) || ignored.existsById(p.rowKey())) {
            return new FormSubmissionResult("ALREADY_IMPORTED", candidates.findFirstBySourceRowKey(p.rowKey()).map(Candidate::getCandidateCode)
                    .orElse(null), List.of());
        }
        if (!p.isValid()) {
            String reason = "Form response skipped - " + String.join("; ", p.errors());
            ignored.save(new IgnoredRow(p.rowKey(), reason));
            audit.recordAs(FORM_ACTOR, "FORM_RESPONSE_SKIPPED", "SYNC", null, reason);
            return new FormSubmissionResult("SKIPPED", null, p.errors());
        }
        String dup = duplicateReason(p);
        if (dup != null) {
            String reason = "Form response from " + p.fullName() + ": duplicate of existing candidate (" + dup + ")";
            ignored.save(new IgnoredRow(p.rowKey(), reason));
            audit.recordAs(FORM_ACTOR, "FORM_RESPONSE_DUPLICATE", "SYNC", null, reason);
            return new FormSubmissionResult("DUPLICATE", null, List.of(reason));
        }
        List<String> warnings = new ArrayList<>(p.warnings());
        Department dept = p.department() == null ? null : departments.findAll().stream().filter(Department::isActive)
                .filter(d -> d.getName().equalsIgnoreCase(p.department())).findFirst().orElse(null);
        if (p.department() != null && dept == null) warnings.add("department \"" + p.department() + "\" is not configured");
        List<FormFile> files = sub.files() == null ? List.of() : sub.files();
        Candidate c = importRow(p, dept, warnings, files.isEmpty() && google.isConfigured());

        List<String> fileProblems = new ArrayList<>();
        for (FormFile f : files) {
            try {
                DocType type = DocType.valueOf(f.field().trim().toUpperCase(Locale.ROOT));
                // the form's declaration covers the Aadhaar consent
                documentService.store(c.getId(), type, f.name(), Base64.getMimeDecoder().decode(f.data()), true, FORM_ACTOR);
            } catch (IllegalArgumentException e) {
                fileProblems.add("file \"" + f.name() + "\" was not readable");
            } catch (ApiException e) {
                fileProblems.add("file \"" + f.name() + "\" not saved: " + e.getMessage());
            }
        }
        if (!fileProblems.isEmpty()) {
            warnings.addAll(fileProblems);
            c.setDataIssues(truncate(String.join("; ", warnings), 2000));
            candidates.save(c);
        }
        return new FormSubmissionResult("CREATED", c.getCandidateCode(), warnings);
    }

    private Candidate importRow(ParsedRow p, Department dept, List<String> rowWarnings, boolean linkDriveFiles) {
        Candidate c = new Candidate();
        c.setFullName(p.fullName());
        c.setDateOfBirth(p.dateOfBirth());
        c.setMobile(p.mobile());
        c.setEmail(p.email());
        c.setAddress(p.address());
        c.setQualification(p.qualification());
        c.setInstitution(p.institution());
        c.setPassingYear(p.passingYear());
        c.setPreviousCompany(p.previousCompany());
        c.setExperienceYears(p.experienceYears());
        c.setPreviousDesignation(p.previousDesignation());
        c.setSkills(p.skills());
        c.setExpectedSalary(p.expectedSalary());
        c.setDepartment(dept);
        c.setDepartmentRaw(p.department());
        c.setPosition(p.position());
        c.setApplicationDate(p.applicationDate());
        c.setAppliedAt(p.appliedAt());
        c.setReferenceName(p.referenceName());
        c.setReferenceRelation(p.referenceRelation());
        c.setReferenceContact(p.referenceContact());
        c.setFamilyRefName(p.familyRefName());
        c.setFamilyRefRelation(p.familyRefRelation());
        c.setFamilyRefContact(p.familyRefContact());
        c.setSource(CandidateSource.GOOGLE_SHEETS);
        c.setSourceRowKey(p.rowKey());
        c.setDataIssues(rowWarnings.isEmpty() ? null : truncate(String.join("; ", rowWarnings), 2000));
        candidates.save(c);
        CandidateService.assignCode(c, candidates);
        candidates.save(c);
        if (linkDriveFiles) {
            addDriveDocs(c, DocType.RESUME, "Resume", p.resumeIds());
            addDriveDocs(c, DocType.AADHAAR, "Aadhaar", p.aadhaarIds());
            addDriveDocs(c, DocType.CERTIFICATE, "Certificate", p.certificateIds());
            addDriveDocs(c, DocType.OTHER, "Document", p.otherDocIds());
        }
        audit.recordAs(p.rowNumber() > 0 ? SYNC_ACTOR : FORM_ACTOR, "CANDIDATE_IMPORTED", "CANDIDATE", c.getId(),
                p.rowNumber() > 0 ? "Imported from Google Form response (sheet row " + p.rowNumber() + ")"
                        : "Imported from Google Form response (sent when the candidate submitted)");
        return c;
    }

    private void addDriveDocs(Candidate c, DocType type, String label, List<String> ids) {
        int n = 1;
        for (String id : ids) {
            String name = "Form upload - " + label + (ids.size() > 1 ? " " + n++ : "");
            documents.save(new CandidateDocument(c, type, CandidateDocument.Storage.DRIVE, name, null, null, id, SYNC_ACTOR));
        }
    }

    private String duplicateReason(ParsedRow p) {
        if (p.email() != null && candidates.existsByEmailIgnoreCase(p.email())) return "same email";
        if (p.mobile() != null && candidates.existsByMobile(p.mobile())) return "same mobile number";
        return null;
    }

    // ---------------------------------------------------------------- helpers

    public List<SyncRunResponse> recentRuns(int limit) {
        return runs.findAllByOrderByStartedAtDesc(org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(limit, 1), 50)))
                .map(SyncRunResponse::from).getContent();
    }

    private SettingsResponse toResponse(IntegrationSettings s) {
        List<FieldInfo> fields = java.util.Arrays.stream(SheetField.values())
                .map(f -> new FieldInfo(f.name(), f.label, f.core)).toList();
        return new SettingsResponse(s.getSpreadsheetId(), s.getSheetRange(), s.getFormUrl(), s.isAutoSyncEnabled(),
                s.getSyncIntervalMinutes(), readMapping(s), fields, google.isConfigured(), google.serviceAccountEmail(),
                s.getFormPushKey(), s.getUpdatedBy(), s.getUpdatedAt());
    }

    private Map<String, String> readMapping(IntegrationSettings s) {
        if (!StringUtils.hasText(s.getColumnMapping())) return Map.of();
        try {
            return mapper.readValue(s.getColumnMapping(), new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            log.warn("Ignoring unreadable column mapping JSON");
            return Map.of();
        }
    }

    private String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void addMessage(List<String> messages, String m) {
        if (messages.size() < MAX_MESSAGES) messages.add(m.replace('\n', ' '));
        else if (messages.size() == MAX_MESSAGES) messages.add("... further messages omitted");
    }

    private static String mask(String id) {
        return id.length() <= 8 ? id : id.substring(0, 4) + "…" + id.substring(id.length() - 4);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static class SyncFailure extends RuntimeException {
        SyncFailure(String message) {
            super(message);
        }
    }
}
