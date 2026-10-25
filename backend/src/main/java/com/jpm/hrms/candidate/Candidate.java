package com.jpm.hrms.candidate;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.department.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "candidates")
public class Candidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String candidateCode;

    @Column(nullable = false)
    private String fullName;
    private LocalDate dateOfBirth;
    private String mobile;
    private String email;
    private String address;

    private String qualification;
    private String institution;
    private Integer passingYear;

    private String previousCompany;
    private BigDecimal experienceYears;
    private String previousDesignation;
    private String skills;
    private String expectedSalary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
    /** Department text as submitted, kept when it does not match a configured department. */
    private String departmentRaw;

    @Column(nullable = false)
    private LocalDate applicationDate;
    @Column(nullable = false)
    private Instant appliedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus applicationStatus = ApplicationStatus.NEW;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus interviewStatus = InterviewStatus.NOT_SCHEDULED;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    private String referenceName;
    private String referenceRelation;
    private String referenceContact;

    /** Family reference: a family member who can be contacted about the candidate. */
    private String familyRefName;
    private String familyRefRelation;
    private String familyRefContact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CandidateSource source;
    @Column(unique = true)
    private String sourceRowKey;
    /** Human-readable notes about missing / invalid fields found during import. */
    private String dataIssues;

    @Version
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCandidateCode() { return candidateCode; }
    public void setCandidateCode(String candidateCode) { this.candidateCode = candidateCode; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }
    public Integer getPassingYear() { return passingYear; }
    public void setPassingYear(Integer passingYear) { this.passingYear = passingYear; }
    public String getPreviousCompany() { return previousCompany; }
    public void setPreviousCompany(String previousCompany) { this.previousCompany = previousCompany; }
    public BigDecimal getExperienceYears() { return experienceYears; }
    public void setExperienceYears(BigDecimal experienceYears) { this.experienceYears = experienceYears; }
    public String getPreviousDesignation() { return previousDesignation; }
    public void setPreviousDesignation(String previousDesignation) { this.previousDesignation = previousDesignation; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getExpectedSalary() { return expectedSalary; }
    public void setExpectedSalary(String expectedSalary) { this.expectedSalary = expectedSalary; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public String getDepartmentRaw() { return departmentRaw; }
    public void setDepartmentRaw(String departmentRaw) { this.departmentRaw = departmentRaw; }
    public LocalDate getApplicationDate() { return applicationDate; }
    public void setApplicationDate(LocalDate applicationDate) { this.applicationDate = applicationDate; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public ApplicationStatus getApplicationStatus() { return applicationStatus; }
    public void setApplicationStatus(ApplicationStatus applicationStatus) { this.applicationStatus = applicationStatus; }
    public InterviewStatus getInterviewStatus() { return interviewStatus; }
    public void setInterviewStatus(InterviewStatus interviewStatus) { this.interviewStatus = interviewStatus; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getReferenceName() { return referenceName; }
    public void setReferenceName(String referenceName) { this.referenceName = referenceName; }
    public String getReferenceRelation() { return referenceRelation; }
    public void setReferenceRelation(String referenceRelation) { this.referenceRelation = referenceRelation; }
    public String getReferenceContact() { return referenceContact; }
    public void setReferenceContact(String referenceContact) { this.referenceContact = referenceContact; }
    public String getFamilyRefName() { return familyRefName; }
    public void setFamilyRefName(String familyRefName) { this.familyRefName = familyRefName; }
    public String getFamilyRefRelation() { return familyRefRelation; }
    public void setFamilyRefRelation(String familyRefRelation) { this.familyRefRelation = familyRefRelation; }
    public String getFamilyRefContact() { return familyRefContact; }
    public void setFamilyRefContact(String familyRefContact) { this.familyRefContact = familyRefContact; }
    public CandidateSource getSource() { return source; }
    public void setSource(CandidateSource source) { this.source = source; }
    public String getSourceRowKey() { return sourceRowKey; }
    public void setSourceRowKey(String sourceRowKey) { this.sourceRowKey = sourceRowKey; }
    public String getDataIssues() { return dataIssues; }
    public void setDataIssues(String dataIssues) { this.dataIssues = dataIssues; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
