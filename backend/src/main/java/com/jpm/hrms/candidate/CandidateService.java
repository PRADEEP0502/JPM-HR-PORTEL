package com.jpm.hrms.candidate;

import com.jpm.hrms.audit.AuditLogRepository;
import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.CandidateDtos.ArchiveRequest;
import com.jpm.hrms.candidate.CandidateDtos.ActivityResponse;
import com.jpm.hrms.candidate.CandidateDtos.CandidateDetail;
import com.jpm.hrms.candidate.CandidateDtos.CandidateListItem;
import com.jpm.hrms.candidate.CandidateDtos.DocumentResponse;
import com.jpm.hrms.candidate.CandidateDtos.RatingChangeResponse;
import com.jpm.hrms.candidate.CandidateDtos.RatingRequest;
import com.jpm.hrms.candidate.CandidateDtos.RemarkResponse;
import com.jpm.hrms.candidate.CandidateDtos.StatusChangeResponse;
import com.jpm.hrms.candidate.CandidateDtos.StatusUpdateRequest;
import com.jpm.hrms.candidate.CandidateDtos.UpdateCandidateRequest;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateSpecifications.Filter;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.common.PageResponse;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentDirectory;
import com.jpm.hrms.department.DepartmentRepository;
import com.jpm.hrms.document.CandidateDocumentRepository;
import com.jpm.hrms.interview.InterviewDtos.InterviewResponse;
import com.jpm.hrms.interview.InterviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class CandidateService {
    /** API sort keys → entity paths. Anything else is rejected. */
    private static final Map<String, String> SORTABLE = Map.of(
            "candidateCode", "id",
            "fullName", "fullName",
            "mobile", "mobile",
            "department", "department.name",
            "qualification", "qualification",
            "experience", "experienceYears",
            "applicationDate", "appliedAt",
            "applicationStatus", "applicationStatus",
            "interviewStatus", "interviewStatus");

    private final CandidateRepository repo;
    private final DepartmentRepository departments;
    private final DepartmentDirectory depts;
    private final CandidateDocumentRepository documents;
    private final InterviewRepository interviews;
    private final HrRemarkRepository remarks;
    private final StatusChangeRepository statusHistory;
    private final RatingChangeRepository ratingHistory;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;

    public CandidateService(CandidateRepository repo, DepartmentRepository departments, DepartmentDirectory depts,
                            CandidateDocumentRepository documents, InterviewRepository interviews, HrRemarkRepository remarks,
                            StatusChangeRepository statusHistory, RatingChangeRepository ratingHistory,
                            AuditLogRepository auditLogs, AuditService audit) {
        this.statusHistory = statusHistory;
        this.ratingHistory = ratingHistory;
        this.repo = repo;
        this.departments = departments;
        this.depts = depts;
        this.documents = documents;
        this.interviews = interviews;
        this.remarks = remarks;
        this.auditLogs = auditLogs;
        this.audit = audit;
    }

    public PageResponse<CandidateListItem> list(Filter filter, int page, int size, String sort, String dir) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Criteria criteria = CandidateSpecifications.matching(filter);
        boolean asc = "asc".equalsIgnoreCase(dir);
        Page<Candidate> result = computedOrder(sort, asc)
                .map(o -> repo.findAllOrdered(criteria, o.key(), o.sort(), pr))
                .orElseGet(() -> repo.findAll(criteria, pr.withSort(toSort(sort, dir))));
        DepartmentDirectory.Names names = depts.snapshot();
        return PageResponse.of(result, c -> CandidateListItem.from(c, names));
    }

    public List<Candidate> listAll(Filter filter, String sort, String dir) {
        Criteria criteria = CandidateSpecifications.matching(filter);
        boolean asc = "asc".equalsIgnoreCase(dir);
        return computedOrder(sort, asc)
                .map(o -> repo.findAllOrdered(criteria, o.key(), o.sort()))
                .orElseGet(() -> repo.findAll(criteria, toSort(sort, dir)));
    }

    private record ComputedOrder(AggregationOperation key, Sort sort) {
    }

    /**
     * Orders that need a computed key: rating keeps unrated candidates last in both directions; department sorts
     * by department name (the name lives in the departments collection) with unassigned candidates last.
     */
    private Optional<ComputedOrder> computedOrder(String sort, boolean asc) {
        if ("rating".equals(sort)) {
            var unrated = ConditionalOperators.when(ComparisonOperators.valueOf("profileRating").greaterThanValue(0)).then(0).otherwise(1);
            return Optional.of(new ComputedOrder(Aggregation.addFields().addField("sortGroup").withValue(unrated).build(),
                    Sort.by(Sort.Order.asc("sortGroup"), asc ? Sort.Order.asc("profileRating") : Sort.Order.desc("profileRating"),
                            Sort.Order.desc("_id"))));
        }
        if ("department".equals(sort)) {
            List<Long> idsByName = departments.findAllByOrderByNameAsc().stream().map(Department::getId).toList();
            var position = ConditionalOperators.when(Criteria.where("departmentId").in(idsByName))
                    .thenValueOf(ArrayOperators.IndexOfArray.arrayOf(idsByName).indexOf("$departmentId"))
                    .otherwise(Integer.MAX_VALUE);
            return Optional.of(new ComputedOrder(Aggregation.addFields().addField("sortGroup").withValue(position).build(),
                    Sort.by(asc ? Sort.Order.asc("sortGroup") : Sort.Order.desc("sortGroup"), Sort.Order.desc("_id"))));
        }
        return Optional.empty();
    }

    /** One pipeline column: a recruitment status, how many active candidates have it, and the newest of them. */
    public record PipelineColumn(ApplicationStatus status, String label, long count, List<CandidateListItem> candidates) {
    }

    /** Active (non-archived) candidates grouped by recruitment status for the pipeline board. */
    public List<PipelineColumn> pipeline(Long departmentId, int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        DepartmentDirectory.Names names = depts.snapshot();
        List<PipelineColumn> columns = new ArrayList<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            Criteria criteria = CandidateSpecifications.matching(new Filter(null, departmentId, null, s, null, null));
            Page<Candidate> page = repo.findAll(criteria, PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
            columns.add(new PipelineColumn(s, s.label, page.getTotalElements(),
                    page.getContent().stream().map(c -> CandidateListItem.from(c, names)).toList()));
        }
        return columns;
    }

    static Sort toSort(String sort, String dir) {
        String path = SORTABLE.getOrDefault(sort == null ? "applicationDate" : sort, "appliedAt");
        Sort.Direction d = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(d, path).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    public CandidateDetail detail(Long id) {
        Candidate c = get(id);
        Department d = depts.find(c.getDepartmentId()).orElse(null);
        return new CandidateDetail(c.getId(), c.getCandidateCode(),
                c.getFullName(), c.getDateOfBirth(), c.getMobile(), c.getEmail(), c.getAddress(),
                c.getQualification(), c.getInstitution(), c.getPassingYear(),
                c.getPreviousCompany(), c.getExperienceYears(), c.getPreviousDesignation(), c.getSkills(), c.getExpectedSalary(),
                d == null ? null : d.getId(), d == null ? null : d.getName(), c.getDepartmentRaw(),
                c.getApplicationDate(), c.getAppliedAt(),
                c.getApplicationStatus(), c.getInterviewStatus(), c.getVerificationStatus(),
                c.getReferenceName(), c.getReferenceRelation(), c.getReferenceContact(),
                c.getFamilyRefName(), c.getFamilyRefRelation(), c.getFamilyRefContact(),
                c.getProfileRating(), c.getRatingRemark(), c.getRatedBy(), c.getRatedAt(),
                c.isArchived(), c.getArchivedAt(), c.getArchivedBy(), c.getArchiveReason(),
                c.getSource(), c.getDataIssues(), c.getVersion(), c.getUpdatedAt(),
                documents.findByCandidateIdOrderByCreatedAtDesc(id).stream().map(DocumentResponse::from).toList(),
                interviews.findByCandidateIdOrderByScheduledAtDesc(id).stream()
                        .map(i -> InterviewResponse.from(i, c, d == null ? c.getDepartmentRaw() : d.getName())).toList(),
                remarks.findByCandidateIdOrderByCreatedAtDesc(id).stream().map(RemarkResponse::from).toList(),
                auditLogs.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("CANDIDATE", id).stream()
                        .filter(a -> !"DOCUMENT_DOWNLOADED".equals(a.getAction()) && !"DOCUMENT_VIEWED".equals(a.getAction()))
                        .limit(50).map(ActivityResponse::from).toList(),
                statusHistory.findByCandidateIdOrderByChangedAtDesc(id).stream().map(StatusChangeResponse::from).toList(),
                ratingHistory.findByCandidateIdOrderByRatedAtDesc(id).stream().map(RatingChangeResponse::from).toList());
    }

    public CandidateDetail update(Long id, UpdateCandidateRequest req) {
        Candidate c = get(id);
        if (c.getVersion() != req.version()) {
            throw new OptimisticLockingFailureException("Candidate " + id + " was changed by someone else");
        }
        List<String> changed = new ArrayList<>();
        String mobile = null;
        if (StringUtils.hasText(req.mobile())) {
            mobile = FieldParsers.normalizeMobile(req.mobile());
            if (mobile == null) throw ApiException.badRequest("Mobile number must have 10–15 digits");
        }
        Department dept = null;
        if (req.departmentId() != null) {
            dept = departments.findById(req.departmentId()).orElseThrow(() -> ApiException.badRequest("Unknown department"));
        }
        set(changed, "Full name", c.getFullName(), req.fullName().trim(), c::setFullName);
        set(changed, "Date of birth", c.getDateOfBirth(), req.dateOfBirth(), c::setDateOfBirth);
        set(changed, "Mobile", c.getMobile(), mobile, c::setMobile);
        set(changed, "Email", c.getEmail(), trim(req.email()), c::setEmail);
        set(changed, "Address", c.getAddress(), trim(req.address()), c::setAddress);
        set(changed, "Qualification", c.getQualification(), trim(req.qualification()), c::setQualification);
        set(changed, "Institution", c.getInstitution(), trim(req.institution()), c::setInstitution);
        set(changed, "Passing year", c.getPassingYear(), req.passingYear(), c::setPassingYear);
        set(changed, "Previous company", c.getPreviousCompany(), trim(req.previousCompany()), c::setPreviousCompany);
        BigDecimal exp = req.experienceYears() == null ? null : req.experienceYears().setScale(1, java.math.RoundingMode.HALF_UP);
        if (!(c.getExperienceYears() == null ? exp == null : exp != null && c.getExperienceYears().compareTo(exp) == 0)) {
            changed.add("Experience");
            c.setExperienceYears(exp);
        }
        set(changed, "Previous designation", c.getPreviousDesignation(), trim(req.previousDesignation()), c::setPreviousDesignation);
        set(changed, "Skills", c.getSkills(), trim(req.skills()), c::setSkills);
        set(changed, "Expected salary", c.getExpectedSalary(), trim(req.expectedSalary()), c::setExpectedSalary);
        if (!Objects.equals(c.getDepartmentId(), req.departmentId())) {
            changed.add("Department");
            c.setDepartment(dept);
        }
        set(changed, "Reference name", c.getReferenceName(), trim(req.referenceName()), c::setReferenceName);
        set(changed, "Reference relation", c.getReferenceRelation(), trim(req.referenceRelation()), c::setReferenceRelation);
        set(changed, "Reference contact", c.getReferenceContact(), trim(req.referenceContact()), c::setReferenceContact);
        set(changed, "Family reference name", c.getFamilyRefName(), trim(req.familyRefName()), c::setFamilyRefName);
        set(changed, "Family reference relationship", c.getFamilyRefRelation(), trim(req.familyRefRelation()), c::setFamilyRefRelation);
        set(changed, "Family reference contact", c.getFamilyRefContact(), trim(req.familyRefContact()), c::setFamilyRefContact);
        if (!changed.isEmpty()) {
            audit.record("CANDIDATE_UPDATED", "CANDIDATE", c.getId(), "Edited: " + String.join(", ", changed));
            repo.save(c);
        }
        return detail(id);
    }

    public CandidateDetail updateStatus(Long id, StatusUpdateRequest req, String actor) {
        Candidate c = get(id);
        if (req.applicationStatus() == null && req.verificationStatus() == null) {
            throw ApiException.badRequest("Provide an application or verification status");
        }
        boolean changed = false;
        if (req.applicationStatus() != null && req.applicationStatus() != c.getApplicationStatus()) {
            audit.record("STATUS_CHANGED", "CANDIDATE", c.getId(), "Application status: " + c.getApplicationStatus().label
                    + " → " + req.applicationStatus().label + (StringUtils.hasText(req.note()) ? " - " + req.note().trim() : ""));
            statusHistory.save(new StatusChange(c.getId(), c.getApplicationStatus(), req.applicationStatus(), actor,
                    StringUtils.hasText(req.note()) ? req.note().trim() : null));
            c.setApplicationStatus(req.applicationStatus());
            changed = true;
        }
        if (req.verificationStatus() != null && req.verificationStatus() != c.getVerificationStatus()) {
            audit.record("VERIFICATION_CHANGED", "CANDIDATE", c.getId(), "Verification: " + c.getVerificationStatus().label
                    + " → " + req.verificationStatus().label);
            c.setVerificationStatus(req.verificationStatus());
            changed = true;
        }
        if (!changed) throw ApiException.badRequest("The candidate already has this status");
        repo.save(c);
        if (StringUtils.hasText(req.note())) remarks.save(new HrRemark(c, actor, req.note().trim()));
        return detail(id);
    }

    /** Sets or changes the Profile Match Rating. The remark (optional) is also kept in the HR remarks history. */
    public CandidateDetail rate(Long id, RatingRequest req, String actor) {
        Candidate c = get(id);
        int rating = req.rating().intValueExact();
        String remark = trim(req.remark());
        if (Objects.equals(c.getProfileRating(), rating) && Objects.equals(c.getRatingRemark(), remark)) {
            throw ApiException.badRequest("The candidate already has this rating");
        }
        Integer old = c.getProfileRating();
        audit.record("RATING_UPDATED", "CANDIDATE", c.getId(), "Profile match rating: "
                + (old == null ? "Not rated" : old + "/10") + " → " + rating + "/10 (" + ratingLabel(rating) + ")");
        c.setProfileRating(rating);
        c.setRatingRemark(remark);
        c.setRatedBy(actor);
        c.setRatedAt(Instant.now());
        repo.save(c);
        ratingHistory.save(new RatingChange(c.getId(), old, rating, remark, actor));
        if (remark != null) remarks.save(new HrRemark(c, actor, "Profile match rating " + rating + "/10: " + remark));
        return detail(id);
    }

    /** Archives (hides from the active list) or restores a candidate. Records are never deleted. */
    public CandidateDetail archive(Long id, ArchiveRequest req, String actor) {
        Candidate c = get(id);
        if (c.isArchived() == req.archived()) {
            throw ApiException.badRequest(req.archived() ? "The candidate is already archived" : "The candidate is not archived");
        }
        String reason = trim(req.reason());
        c.setArchived(req.archived());
        c.setArchivedAt(req.archived() ? Instant.now() : null);
        c.setArchivedBy(req.archived() ? actor : null);
        c.setArchiveReason(req.archived() ? reason : null);
        repo.save(c);
        audit.record(req.archived() ? "CANDIDATE_ARCHIVED" : "CANDIDATE_RESTORED", "CANDIDATE", c.getId(),
                (req.archived() ? "Archived" : "Restored from archive") + (reason != null ? " - " + reason : ""));
        return detail(id);
    }

    /** Guideline label for a rating; HR makes the final call. */
    public static String ratingLabel(int rating) {
        if (rating <= 2) return "Not Suitable";
        if (rating <= 4) return "Low Match";
        if (rating <= 6) return "Average Match";
        if (rating <= 8) return "Good Match";
        return "Excellent Match";
    }

    public RemarkResponse addRemark(Long id, String text, String actor) {
        Candidate c = get(id);
        HrRemark r = remarks.save(new HrRemark(c, actor, text.trim()));
        audit.record("REMARK_ADDED", "CANDIDATE", c.getId(), "HR remark added");
        return RemarkResponse.from(r);
    }

    /**
     * Assigns the human-readable ID after the row has its database id: a per-year serial (YYYY-N, e.g. 2026-1)
     * for real applicants, DEMO-N for demo rows so they never use up real serial numbers.
     */
    public static void assignCode(Candidate c, CandidateRepository repo) {
        if (c.getSource() == CandidateSource.DEMO) {
            c.setCandidateCode("DEMO-" + c.getId());
            return;
        }
        int year = (c.getApplicationDate() == null ? LocalDate.now() : c.getApplicationDate()).getYear();
        long serial = repo.countBySourceNotAndApplicationDateBetweenAndIdLessThanEqual(
                CandidateSource.DEMO, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), c.getId());
        c.setCandidateCode(year + "-" + serial);
    }

    private Candidate get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Candidate"));
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static <T> void set(List<String> changed, String label, T oldValue, T newValue, java.util.function.Consumer<T> setter) {
        if (!Objects.equals(oldValue, newValue)) {
            changed.add(label);
            setter.accept(newValue);
        }
    }
}
