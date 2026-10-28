package com.jpm.hrms.sync;

import java.util.List;
import java.util.Locale;

/**
 * Candidate fields that can be read from the Google Form response sheet, with the header texts
 * that are recognised automatically. Admins can override any mapping from Settings when the form
 * uses different question titles.
 */
public enum SheetField {
    TIMESTAMP("Timestamp", true, "timestamp", "submitted at", "submission time"),
    FULL_NAME("Full name", true, "full name", "name", "candidate name", "applicant name", "your name"),
    DATE_OF_BIRTH("Date of birth", false, "date of birth", "dob", "birth date"),
    MOBILE("Mobile number", false, "mobile number", "mobile", "mobile no", "phone", "phone number", "contact number", "whatsapp number"),
    EMAIL("Email", false, "email", "email address", "email id", "e-mail"),
    ADDRESS("Address", false, "address", "current address", "residential address", "permanent address"),
    QUALIFICATION("Qualification", false, "qualification", "highest qualification", "educational qualification", "degree"),
    INSTITUTION("Institution", false, "institution", "institution name", "college", "university", "college/university"),
    PASSING_YEAR("Passing year", false, "passing year", "year of passing", "graduation year"),
    PREVIOUS_COMPANY("Previous company", false, "previous company", "current company", "last company", "previous employer", "current employer"),
    EXPERIENCE("Total experience", false, "total experience", "experience", "experience in years", "total experience in years", "years of experience"),
    PREVIOUS_DESIGNATION("Previous designation", false, "previous designation", "current designation", "designation", "last designation"),
    SKILLS("Skills", false, "skills", "key skills", "technical skills"),
    EXPECTED_SALARY("Expected salary", false, "expected salary", "expected ctc", "salary expectation"),
    DEPARTMENT("Department", false, "department", "applied department", "applying for", "department applied for"),
    /** Sub-work: the form has one "Position (<Department>)" question per department section; the first answered one is used. */
    POSITION("Sub-work / position", false, "position", "sub work", "subwork", "post applied for", "position applied for", "job role", "role"),
    REFERENCE_NAME("Reference name", false, "reference name", "referee name", "reference"),
    REFERENCE_RELATION("Reference relationship / designation", false, "reference relationship", "reference designation", "relationship", "reference relation", "relationship or designation"),
    REFERENCE_CONTACT("Reference contact", false, "reference contact", "reference contact details", "reference phone", "referee contact"),
    FAMILY_REF_NAME("Family reference name", false, "family reference name", "family member name", "name of family member", "family contact name", "family reference"),
    FAMILY_REF_RELATION("Family reference relationship", false, "family reference relationship", "family reference relation", "relationship with family member", "family member relationship", "relationship with candidate"),
    FAMILY_REF_CONTACT("Family reference contact", false, "family reference contact", "family reference contact number", "family reference mobile", "family member contact", "family member mobile", "family contact number"),
    RESUME("Resume upload", false, "resume", "upload resume", "resume/cv", "cv", "upload your resume", "resume upload"),
    AADHAAR("Aadhaar card upload", false, "aadhaar card upload", "aadhar card upload", "upload aadhaar", "upload aadhar", "aadhaar card", "aadhar card", "aadhaar copy", "aadhar copy"),
    CERTIFICATES("Educational certificates", false, "educational certificates", "certificates", "upload certificates", "education certificates"),
    OTHER_DOCUMENTS("Other documents", false, "other documents", "additional documents", "supporting documents");

    public final String label;
    public final boolean core;
    public final List<String> aliases;

    SheetField(String label, boolean core, String... aliases) {
        this.label = label;
        this.core = core;
        this.aliases = List.of(aliases);
    }

    /** Lower-case, alphanumerics only - so "E-mail Address:" and "email address" compare equal. */
    public static String normalize(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
