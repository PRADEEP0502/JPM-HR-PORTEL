package com.jpm.hrms.candidate;

import com.jpm.hrms.audit.AuditLog;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.department.DepartmentDirectory;
import com.jpm.hrms.document.CandidateDocument;
import com.jpm.hrms.interview.InterviewDtos.InterviewResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CandidateDtos {
    private CandidateDtos() {
    }

    public record CandidateListItem(Long id, String candidateCode, String fullName, String mobile, String email,
                                    Long departmentId, String departmentName, String qualification,
                                    BigDecimal experienceYears, LocalDate applicationDate,
                                    ApplicationStatus applicationStatus, InterviewStatus interviewStatus,
                                    VerificationStatus verificationStatus, CandidateSource source,
                                    Integer profileRating, String ratingRemark, boolean archived) {
        public static CandidateListItem from(Candidate c, DepartmentDirectory.Names depts) {
            return new CandidateListItem(c.getId(), c.getCandidateCode(), c.getFullName(), c.getMobile(), c.getEmail(),
                    depts.name(c) == null ? null : c.getDepartmentId(),
                    depts.nameOrRaw(c),
                    c.getQualification(), c.getExperienceYears(), c.getApplicationDate(), c.getApplicationStatus(),
                    c.getInterviewStatus(), c.getVerificationStatus(), c.getSource(),
                    c.getProfileRating(), c.getRatingRemark(), c.isArchived());
        }
    }

    public record DocumentResponse(Long id, CandidateDocument.DocType docType, CandidateDocument.Storage storage,
                                   String fileName, String contentType, Long sizeBytes, String uploadedBy, boolean encrypted,
                                   Instant createdAt, CandidateDocument.ScanStatus scanStatus) {
        public static DocumentResponse from(CandidateDocument d) {
            return new DocumentResponse(d.getId(), d.getDocType(), d.getStorage(), d.getFileName(), d.getContentType(),
                    d.getSizeBytes(), d.getUploadedBy(), d.isEncrypted(), d.getCreatedAt(), d.getScanStatus());
        }
    }

    public record RemarkResponse(Long id, String author, String remark, Instant createdAt) {
        public static RemarkResponse from(HrRemark r) {
            return new RemarkResponse(r.getId(), r.getAuthor(), r.getRemark(), r.getCreatedAt());
        }
    }

    public record ActivityResponse(Instant at, String actor, String action, String details) {
        public static ActivityResponse from(AuditLog a) {
            return new ActivityResponse(a.getOccurredAt(), a.getActor(), a.getAction(), a.getDetails());
        }
    }

    public record CandidateDetail(
            Long id, String candidateCode,
            // A. personal
            String fullName, LocalDate dateOfBirth, String mobile, String email, String address,
            // B. education
            String qualification, String institution, Integer passingYear,
            // C. employment
            String previousCompany, BigDecimal experienceYears, String previousDesignation, String skills,
            String expectedSalary,
            // D. application
            Long departmentId, String departmentName, String departmentRaw, LocalDate applicationDate, Instant appliedAt,
            ApplicationStatus applicationStatus, InterviewStatus interviewStatus, VerificationStatus verificationStatus,
            // E. references
            String referenceName, String referenceRelation, String referenceContact,
            // E. family reference
            String familyRefName, String familyRefRelation, String familyRefContact,
            // F. profile match rating (HR only)
            Integer profileRating, String ratingRemark, String ratedBy, Instant ratedAt,
            // archive
            boolean archived, Instant archivedAt, String archivedBy, String archiveReason,
            // meta
            CandidateSource source, String dataIssues, long version, Instant updatedAt,
            List<DocumentResponse> documents, List<InterviewResponse> interviews,
            List<RemarkResponse> remarks, List<ActivityResponse> activity,
            List<StatusChangeResponse> statusHistory, List<RatingChangeResponse> ratingHistory) {
    }

    public record StatusChangeResponse(ApplicationStatus fromStatus, ApplicationStatus toStatus, String changedBy,
                                       Instant changedAt, String note) {
        public static StatusChangeResponse from(StatusChange s) {
            return new StatusChangeResponse(s.getFromStatus(), s.getToStatus(), s.getChangedBy(), s.getChangedAt(), s.getNote());
        }
    }

    public record RatingChangeResponse(Integer fromRating, Integer toRating, String remark, String ratedBy, Instant ratedAt) {
        public static RatingChangeResponse from(RatingChange r) {
            return new RatingChangeResponse(r.getFromRating(), r.getToRating(), r.getRemark(), r.getRatedBy(), r.getRatedAt());
        }
    }

    public record ArchiveRequest(boolean archived, @Size(max = 500) String reason) {
    }

    private static final String PHONE = "^\\+?[0-9 ()-]{10,20}$";

    /** Fields HR may correct. Status fields are changed through the dedicated status endpoint. */
    public record UpdateCandidateRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Past LocalDate dateOfBirth,
            @Pattern(regexp = PHONE, message = "must be a valid phone number") String mobile,
            @Email @Size(max = 200) String email,
            @Size(max = 1000) String address,
            @Size(max = 200) String qualification,
            @Size(max = 200) String institution,
            @Min(1950) @Max(2100) Integer passingYear,
            @Size(max = 200) String previousCompany,
            @DecimalMin("0") @DecimalMax("60") BigDecimal experienceYears,
            @Size(max = 150) String previousDesignation,
            @Size(max = 2000) String skills,
            @Size(max = 100) String expectedSalary,
            Long departmentId,
            @Size(max = 150) String referenceName,
            @Size(max = 150) String referenceRelation,
            @Size(max = 200) String referenceContact,
            @Size(max = 150) String familyRefName,
            @Size(max = 100) String familyRefRelation,
            @Pattern(regexp = PHONE, message = "must be a valid phone number") String familyRefContact,
            long version) {
    }

    public record StatusUpdateRequest(ApplicationStatus applicationStatus, VerificationStatus verificationStatus,
                                      @Size(max = 2000) String note) {
    }

    private static final String RATING_RULE = "must be a whole number from 1 to 10";

    /** Profile Match Rating: whole numbers 1-10 only (7.5 or 7.0 are rejected, not rounded). */
    public record RatingRequest(@NotNull(message = RATING_RULE) @Digits(integer = 2, fraction = 0, message = RATING_RULE)
                                @Min(value = 1, message = RATING_RULE) @Max(value = 10, message = RATING_RULE) BigDecimal rating,
                                @Size(max = 2000) String remark) {
    }

    public record RemarkRequest(@NotBlank @Size(max = 2000) String remark) {
    }
}
