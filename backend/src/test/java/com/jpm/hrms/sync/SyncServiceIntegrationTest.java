package com.jpm.hrms.sync;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.communication.CommunicationRepository;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import com.jpm.hrms.document.CandidateDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Runs the real sync pipeline against an in-memory DB with the Google API mocked out. */
@SpringBootTest
@ActiveProfiles("test")
class SyncServiceIntegrationTest {

    @MockitoBean
    GoogleApiClient google;

    @Autowired
    SyncService sync;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    CandidateDocumentRepository documents;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    IgnoredRowRepository ignored;
    @Autowired
    CommunicationRepository communications;

    private final List<List<Object>> sheet = new ArrayList<>();

    @BeforeEach
    void setUp() {
        communications.deleteAll();
        documents.deleteAll();
        candidates.deleteAll();
        ignored.deleteAll();
        if (departments.findByNameIgnoreCase("Engineering").isEmpty()) departments.save(new Department("Engineering", null));
        when(google.isConfigured()).thenReturn(true);
        when(google.readValues(anyString(), anyString())).thenAnswer(inv -> sheet);
        sheet.clear();
        sheet.add(List.of("Timestamp", "Full Name", "Email Address", "Mobile Number", "Department", "Total Experience", "Upload Resume"));
    }

    @Test
    void importsNewRowsSkipsInvalidAndDuplicatesAndIsIdempotent() {
        sheet.add(List.of(new BigDecimal("46315.40"), "Asha Rao", "asha@example.com", "9876543210", "engineering", "3 years",
                "https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz"));
        sheet.add(List.of(new BigDecimal("46315.41"), "No Contact", "", "", "Engineering", "", ""));
        sheet.add(List.of(new BigDecimal("46315.42"), "Asha R (again)", "ASHA@example.com", "9000000000", "Engineering", "", ""));
        sheet.add(List.of(new BigDecimal("46315.43"), "Ravi Kumar", "ravi@example.com", "", "Astronomy", "lots", ""));

        var run = sync.runNow("test");
        assertThat(run.status()).isEqualTo(SyncRun.Status.PARTIAL);
        assertThat(run.createdCount()).isEqualTo(2);
        assertThat(run.skippedCount()).isEqualTo(1);
        assertThat(run.duplicateCount()).isEqualTo(1);
        assertThat(run.warningCount()).isEqualTo(1);
        assertThat(run.messages()).anyMatch(m -> m.contains("Row 3") && m.contains("no valid mobile number or email"));

        var asha = candidates.findAll().stream().filter(c -> c.getEmail().equals("asha@example.com")).findFirst().orElseThrow();
        assertThat(asha.getCandidateCode()).matches("\\d{4}-[1-9]\\d*");
        assertThat(asha.getSource()).isEqualTo(CandidateSource.GOOGLE_SHEETS);
        assertThat(asha.getApplicationStatus()).isEqualTo(ApplicationStatus.NEW);
        assertThat(asha.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(asha.getDepartmentId()).isNotNull();
        assertThat(asha.getExperienceYears()).isEqualByComparingTo("3");
        assertThat(documents.findByCandidateIdOrderByCreatedAtDesc(asha.getId())).hasSize(1);

        var ravi = candidates.findAll().stream().filter(c -> c.getEmail().equals("ravi@example.com")).findFirst().orElseThrow();
        assertThat(ravi.getDepartmentId()).isNull();
        assertThat(ravi.getDepartmentRaw()).isEqualTo("Astronomy");
        assertThat(ravi.getDataIssues()).contains("unreadable experience").contains("not configured");

        // Second run: nothing new, nothing re-reported.
        var again = sync.runNow("test");
        assertThat(again.status()).isEqualTo(SyncRun.Status.SUCCESS);
        assertThat(again.createdCount()).isZero();
        assertThat(again.messages()).isEmpty();
        assertThat(candidates.count()).isEqualTo(2);

        // A new submission appears.
        sheet.add(List.of(new BigDecimal("46316.10"), "Meera Iyer", "meera@example.com", "9123456789", "Engineering", "Fresher", ""));
        var third = sync.runNow("test");
        assertThat(third.createdCount()).isEqualTo(1);
        assertThat(candidates.count()).isEqualTo(3);
    }

    @Test
    void failsClearlyWhenSheetIsNotAFormResponseSheet() {
        sheet.clear();
        sheet.add(List.of("Col A", "Col B"));
        var run = sync.runNow("test");
        assertThat(run.status()).isEqualTo(SyncRun.Status.FAILED);
        assertThat(run.errorMessage()).contains("Timestamp");
    }

    @Test
    void reportsUnconfiguredCredentials() {
        when(google.isConfigured()).thenReturn(false);
        var run = sync.runNow("test");
        assertThat(run.status()).isEqualTo(SyncRun.Status.FAILED);
        assertThat(run.errorMessage()).contains("credentials");
    }
}
