package com.jpm.hrms.sync;

import com.jpm.hrms.candidate.FieldParsers;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SheetRowMapperTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    void resolvesHeadersByAliasAndPrefix() {
        var res = SheetRowMapper.resolveColumns(List.of("Timestamp", "Full Name (as per certificates)", "Email Address",
                "Mobile No.", "Total Experience", "Applying for", "Upload Resume", "Favourite colour"), Map.of());
        assertThat(res.problems()).isEmpty();
        assertThat(res.columns()).containsEntry(SheetField.FULL_NAME, 1).containsEntry(SheetField.EMAIL, 2)
                .containsEntry(SheetField.MOBILE, 3).containsEntry(SheetField.EXPERIENCE, 4)
                .containsEntry(SheetField.DEPARTMENT, 5).containsEntry(SheetField.RESUME, 6);
        assertThat(res.unmappedHeaders()).containsExactly("Favourite colour");
    }

    @Test
    void mapsFamilyReferenceSeparatelyFromProfessionalReference() {
        var cols = SheetRowMapper.resolveColumns(List.of("Timestamp", "Full Name", "Email", "Reference Name", "Reference Contact",
                "Family Reference Name", "Relationship with Family Member", "Family Reference Contact Number"), Map.of());
        assertThat(cols.problems()).isEmpty();
        assertThat(cols.columns()).containsEntry(SheetField.REFERENCE_NAME, 3).containsEntry(SheetField.REFERENCE_CONTACT, 4)
                .containsEntry(SheetField.FAMILY_REF_NAME, 5).containsEntry(SheetField.FAMILY_REF_RELATION, 6)
                .containsEntry(SheetField.FAMILY_REF_CONTACT, 7);
        var p = SheetRowMapper.parse(List.of(new BigDecimal("46315.5"), "Asha Rao", "asha@example.com", "Ravi K", "9876500000",
                "Mohan Rao", "Father", "9876511111"), 2, cols.columns(), IST, TODAY);
        assertThat(p.familyRefName()).isEqualTo("Mohan Rao");
        assertThat(p.familyRefRelation()).isEqualTo("Father");
        assertThat(p.familyRefContact()).isEqualTo("9876511111");
        assertThat(p.referenceName()).isEqualTo("Ravi K");
    }

    @Test
    void takesSubWorkFromTheAnsweredDepartmentSection() {
        var res = SheetRowMapper.resolveColumns(List.of("Timestamp", "Full Name", "Email", "Department",
                "Position (Production)", "Position (Quality Control)", "Position (Office)", "Declaration"), Map.of());
        assertThat(res.problems()).isEmpty();
        assertThat(res.columns()).containsEntry(SheetField.DEPARTMENT, 3);
        assertThat(res.positionColumns()).containsExactly(4, 5, 6);
        assertThat(res.unmappedHeaders()).containsExactly("Declaration");
        var p = SheetRowMapper.parse(List.of("10/20/2026 9:15:00", "Asha Rao", "asha@example.com", "Office", "", "", "Accountant"),
                2, res.columns(), res.positionColumns(), IST, TODAY);
        assertThat(p.department()).isEqualTo("Office");
        assertThat(p.position()).isEqualTo("Accountant");
    }

    /** Exact header row produced by google-form/create-jpm-application-form.gs (+ the 3 manual upload questions). */
    @Test
    void recognisesEveryColumnOfTheJpmGoogleForm() {
        List<Object> headers = List.of("Timestamp", "Full Name", "Date of Birth", "Mobile Number", "Email", "Address",
                "Highest Qualification", "Institution", "Year of Passing", "Total Experience (in years)", "Previous Company",
                "Previous Designation", "Key Skills", "Expected Salary", "Department", "Reference Name",
                "Reference Relationship / Designation", "Reference Contact Number", "Family Reference Name",
                "Relationship with Family Member", "Family Reference Contact Number", "Declaration", "Upload Resume",
                "Aadhaar Card Upload", "Educational Certificates");
        var res = SheetRowMapper.resolveColumns(headers, Map.of());
        assertThat(res.problems()).isEmpty();
        assertThat(res.unmappedHeaders()).containsExactly("Declaration");
        assertThat(res.columns()).containsEntry(SheetField.TIMESTAMP, 0).containsEntry(SheetField.FULL_NAME, 1)
                .containsEntry(SheetField.DATE_OF_BIRTH, 2).containsEntry(SheetField.MOBILE, 3).containsEntry(SheetField.EMAIL, 4)
                .containsEntry(SheetField.ADDRESS, 5).containsEntry(SheetField.QUALIFICATION, 6).containsEntry(SheetField.INSTITUTION, 7)
                .containsEntry(SheetField.PASSING_YEAR, 8).containsEntry(SheetField.EXPERIENCE, 9).containsEntry(SheetField.PREVIOUS_COMPANY, 10)
                .containsEntry(SheetField.PREVIOUS_DESIGNATION, 11).containsEntry(SheetField.SKILLS, 12).containsEntry(SheetField.EXPECTED_SALARY, 13)
                .containsEntry(SheetField.DEPARTMENT, 14).containsEntry(SheetField.REFERENCE_NAME, 15)
                .containsEntry(SheetField.REFERENCE_RELATION, 16).containsEntry(SheetField.REFERENCE_CONTACT, 17)
                .containsEntry(SheetField.FAMILY_REF_NAME, 18).containsEntry(SheetField.FAMILY_REF_RELATION, 19)
                .containsEntry(SheetField.FAMILY_REF_CONTACT, 20).containsEntry(SheetField.RESUME, 22)
                .containsEntry(SheetField.AADHAAR, 23).containsEntry(SheetField.CERTIFICATES, 24);

        // A realistic response row as Google Sheets returns it (dates as serial numbers, uploads as Drive links)
        List<Object> row = List.of(new BigDecimal("46318.4375"), "Priya Sharma", new BigDecimal("36161"), "98765 43210",
                "priya@example.com", "12, Gandhi Road, Coimbatore 641001", "B.E. Mechanical", "PSG College of Technology", "2021",
                "Fresher", "", "", "AutoCAD, Lathe operation", "₹25,000 per month", "Engineering", "Dr. K. Raman", "HOD",
                "9443012345", "Suresh Sharma", "Father", "9443098765",
                "I confirm that the information given is true, and I consent to JPM – Junior Processing Mill using it for recruitment purposes.",
                "https://drive.google.com/open?id=1ResumeFileIdAbCdEfGhIjKlMn", "https://drive.google.com/open?id=1AadhaarFileIdAbCdEfGhIjKl", "");
        var p = SheetRowMapper.parse(row, 2, res.columns(), IST, TODAY);
        assertThat(p.isValid()).isTrue();
        assertThat(p.warnings()).isEmpty();
        assertThat(p.mobile()).isEqualTo("9876543210");
        assertThat(p.dateOfBirth()).isEqualTo(LocalDate.of(1999, 1, 1));
        assertThat(p.experienceYears()).isEqualByComparingTo("0");
        assertThat(p.passingYear()).isEqualTo(2021);
        assertThat(p.department()).isEqualTo("Engineering");
        assertThat(p.familyRefRelation()).isEqualTo("Father");
        assertThat(p.referenceRelation()).isEqualTo("HOD");
        assertThat(p.resumeIds()).containsExactly("1ResumeFileIdAbCdEfGhIjKlMn");
        assertThat(p.aadhaarIds()).containsExactly("1AadhaarFileIdAbCdEfGhIjKl");
        assertThat(p.certificateIds()).isEmpty();
    }

    @Test
    void adminOverrideWins() {
        var res = SheetRowMapper.resolveColumns(List.of("Timestamp", "Name", "Applicant", "Email"),
                Map.of("FULL_NAME", "Applicant"));
        assertThat(res.columns()).containsEntry(SheetField.FULL_NAME, 2);
    }

    @Test
    void reportsMissingCoreColumns() {
        var res = SheetRowMapper.resolveColumns(List.of("Question 1", "Question 2"), Map.of());
        assertThat(res.problems()).hasSize(3);
    }

    @Test
    void parsesRowWithSerialDatesAndWarnings() {
        var cols = SheetRowMapper.resolveColumns(List.of("Timestamp", "Full name", "Email", "Mobile number",
                "Date of birth", "Total experience", "Resume"), Map.of()).columns();
        // 46315.5 = 2026-10-20 12:00 (Sheets serial)
        List<Object> row = List.of(new BigDecimal("46315.5"), "  Asha  Rao ", "ASHA@Example.com", "+91 98765-43210",
                new BigDecimal("35000"), "2 years 6 months", "https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz");
        var p = SheetRowMapper.parse(row, 2, cols, IST, TODAY);
        assertThat(p.isValid()).isTrue();
        assertThat(p.fullName()).isEqualTo("Asha Rao");
        assertThat(p.email()).isEqualTo("asha@example.com");
        assertThat(p.mobile()).isEqualTo("9876543210");
        assertThat(p.applicationDate()).isEqualTo(TODAY);
        assertThat(p.dateOfBirth()).isEqualTo(LocalDate.of(1995, 10, 28));
        assertThat(p.experienceYears()).isEqualByComparingTo("2.5");
        assertThat(p.resumeIds()).containsExactly("1AbCdEfGhIjKlMnOpQrStUvWxYz");
        assertThat(p.warnings()).isEmpty();
    }

    @Test
    void rowWithoutContactIsInvalidAndBadEmailIsWarning() {
        var cols = SheetRowMapper.resolveColumns(List.of("Timestamp", "Full name", "Email", "Mobile number"), Map.of()).columns();
        var p = SheetRowMapper.parse(List.of("10/20/2026 9:15:00", "No Contact", "not-an-email", "123"), 3, cols, IST, TODAY);
        assertThat(p.isValid()).isFalse();
        assertThat(p.errors()).contains("no valid mobile number or email");
        assertThat(p.warnings()).hasSize(2);
    }

    @Test
    void rowKeyIsStable() {
        var cols = SheetRowMapper.resolveColumns(List.of("Timestamp", "Full name", "Email"), Map.of()).columns();
        var a = SheetRowMapper.parse(List.of(new BigDecimal("46315.25"), "A B", "a@b.co"), 2, cols, IST, TODAY);
        var b = SheetRowMapper.parse(List.of(new BigDecimal("46315.25"), "A B", "a@b.co"), 9, cols, IST, TODAY);
        assertThat(a.rowKey()).isEqualTo(b.rowKey());
    }

    @Test
    void fieldParsers() {
        assertThat(FieldParsers.parseExperience("Fresher")).isEqualByComparingTo("0");
        assertThat(FieldParsers.parseExperience("8 months")).isEqualByComparingTo("0.7");
        assertThat(FieldParsers.parseExperience("about 4")).isEqualByComparingTo("4");
        assertThat(FieldParsers.parseExperience("lots")).isNull();
        assertThat(FieldParsers.parseDate("31/02/1998")).isNull();
        assertThat(FieldParsers.parseDate("05/11/1998")).isEqualTo(LocalDate.of(1998, 11, 5));
        assertThat(FieldParsers.normalizeMobile("09876543210")).isEqualTo("9876543210");
        assertThat(FieldParsers.normalizeMobile("12345")).isNull();
        // every format the Google Form's mobile validation accepts normalises to the same number
        for (String formatted : List.of("9876543210", "98765 43210", "98765-43210", "09876543210", "+919876543210",
                "+91 9876543210", "91-98765 43210")) {
            assertThat(FieldParsers.normalizeMobile(formatted)).as(formatted).isEqualTo("9876543210");
            assertThat(com.jpm.hrms.communication.ContactValidator.checkPhone(formatted, "91").international())
                    .as(formatted).isEqualTo("919876543210");
        }
        assertThat(FieldParsers.parseYear("Passed in 2019", 2027)).isEqualTo(2019);
    }
}
