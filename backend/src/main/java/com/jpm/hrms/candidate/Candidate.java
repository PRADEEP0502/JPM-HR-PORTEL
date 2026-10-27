package com.jpm.hrms.candidate;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.common.SequencedEntity;
import com.jpm.hrms.department.Department;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Document("candidates")
public class Candidate implements SequencedEntity {
    @Id
    private Long id;

    private String candidateCode;

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

    /** Id of the configured department (see {@link com.jpm.hrms.department.DepartmentDirectory} for its name). */
    private Long departmentId;
    /** Department text as submitted, kept when it does not match a configured department. */
    private String departmentRaw;

    private LocalDate applicationDate;
    private Instant appliedAt;

    private ApplicationStatus applicationStatus = ApplicationStatus.NEW;
    private InterviewStatus interviewStatus = InterviewStatus.NOT_SCHEDULED;
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    private String referenceName;
    private String referenceRelation;
    private String referenceContact;

    /** Family reference: a family member who can be contacted about the candidate. */
    private String familyRefName;
    private String familyRefRelation;
    private String familyRefContact;

    /** Profile Match Rating (1-10) set by HR after reviewing the application; null = not rated yet. */
    private Integer profileRating;
    private String ratingRemark;
    private String ratedBy;
    private Instant ratedAt;

    /** Archived candidates are hidden from the active candidate list; nothing is deleted. */
    private boolean archived;
    private Instant archivedAt;
    private String archivedBy;
    private String archiveReason;

    private CandidateSource source;
    private String sourceRowKey;
    /** Human-readable notes about missing / invalid fields found during import. */
    private String dataIssues;

    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    @Override
    public void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @Override
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    @Override
    public void assignId(Long id) { this.id = id; }
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
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    /** Convenience for callers holding a Department: stores its id (null clears the department). */
    public void setDepartment(Department department) { this.departmentId = department == null ? null : department.getId(); }
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
    public Integer getProfileRating() { return profileRating; }
    public void setProfileRating(Integer profileRating) { this.profileRating = profileRating; }
    public String getRatingRemark() { return ratingRemark; }
    public void setRatingRemark(String ratingRemark) { this.ratingRemark = ratingRemark; }
    public String getRatedBy() { return ratedBy; }
    public void setRatedBy(String ratedBy) { this.ratedBy = ratedBy; }
    public Instant getRatedAt() { return ratedAt; }
    public void setRatedAt(Instant ratedAt) { this.ratedAt = ratedAt; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public String getArchivedBy() { return archivedBy; }
    public void setArchivedBy(String archivedBy) { this.archivedBy = archivedBy; }
    public String getArchiveReason() { return archiveReason; }
    public void setArchiveReason(String archiveReason) { this.archiveReason = archiveReason; }
    public CandidateSource getSource() { return source; }
    public void setSource(CandidateSource source) { this.source = source; }
    public String getSourceRowKey() { return sourceRowKey; }
    public void setSourceRowKey(String sourceRowKey) { this.sourceRowKey = sourceRowKey; }
    public String getDataIssues() { return dataIssues; }
    public void setDataIssues(String dataIssues) { this.dataIssues = dataIssues; }
    public long getVersion() { return version == null ? 0 : version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
