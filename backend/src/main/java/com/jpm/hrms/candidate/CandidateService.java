package com.jpm.hrms.candidate;

import com.jpm.hrms.audit.AuditLogRepository;
import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.CandidateDtos.ActivityResponse;
import com.jpm.hrms.candidate.CandidateDtos.CandidateDetail;
import com.jpm.hrms.candidate.CandidateDtos.CandidateListItem;
import com.jpm.hrms.candidate.CandidateDtos.DocumentResponse;
import com.jpm.hrms.candidate.CandidateDtos.RemarkResponse;
import com.jpm.hrms.candidate.CandidateDtos.StatusUpdateRequest;
import com.jpm.hrms.candidate.CandidateDtos.UpdateCandidateRequest;
import com.jpm.hrms.candidate.CandidateSpecifications.Filter;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.common.PageResponse;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import com.jpm.hrms.document.CandidateDocumentRepository;
import com.jpm.hrms.interview.InterviewDtos.InterviewResponse;
import com.jpm.hrms.interview.InterviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final CandidateDocumentRepository documents;
    private final InterviewRepository interviews;
    private final HrRemarkRepository remarks;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;

    public CandidateService(CandidateRepository repo, DepartmentRepository departments, CandidateDocumentRepository documents,
                            InterviewRepository interviews, HrRemarkRepository remarks, AuditLogRepository auditLogs,
                            AuditService audit) {
        this.repo = repo;
        this.departments = departments;
        this.documents = documents;
        this.interviews = interviews;
        this.remarks = remarks;
        this.auditLogs = auditLogs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<CandidateListItem> list(Filter filter, int page, int size, String sort, String dir) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), toSort(sort, dir));
        return PageResponse.of(repo.findAll(CandidateSpecifications.matching(filter), pr), CandidateListItem::from);
    }

    @Transactional(readOnly = true)
    public List<Candidate> listAll(Filter filter, String sort, String dir) {
        return repo.findAll(CandidateSpecifications.matching(filter), toSort(sort, dir));
    }

    static Sort toSort(String sort, String dir) {
        String path = SORTABLE.getOrDefault(sort == null ? "applicationDate" : sort, "appliedAt");
        Sort.Direction d = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(d, path).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Transactional(readOnly = true)
    public CandidateDetail detail(Long id) {
        Candidate c = get(id);
        Department d = c.getDepartment();
        return new CandidateDetail(c.getId(), c.getCandidateCode(),
                c.getFullName(), c.getDateOfBirth(), c.getMobile(), c.getEmail(), c.getAddress(),
                c.getQualification(), c.getInstitution(), c.getPassingYear(),
                c.getPreviousCompany(), c.getExperienceYears(), c.getPreviousDesignation(), c.getSkills(), c.getExpectedSalary(),
                d == null ? null : d.getId(), d == null ? null : d.getName(), c.getDepartmentRaw(),
                c.getApplicationDate(), c.getAppliedAt(),
                c.getApplicationStatus(), c.getInterviewStatus(), c.getVerificationStatus(),
                c.getReferenceName(), c.getReferenceRelation(), c.getReferenceContact(),
                c.getFamilyRefName(), c.getFamilyRefRelation(), c.getFamilyRefContact(),
                c.getSource(), c.getDataIssues(), c.getVersion(), c.getUpdatedAt(),
                documents.findByCandidateIdOrderByCreatedAtDesc(id).stream().map(DocumentResponse::from).toList(),
                interviews.findByCandidateIdOrderByScheduledAtDesc(id).stream().map(InterviewResponse::from).toList(),
                remarks.findByCandidateIdOrderByCreatedAtDesc(id).stream().map(RemarkResponse::from).toList(),
                auditLogs.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("CANDIDATE", id).stream()
                        .filter(a -> !"DOCUMENT_DOWNLOADED".equals(a.getAction()))
                        .limit(50).map(ActivityResponse::from).toList());
    }

    @Transactional
    public CandidateDetail update(Long id, UpdateCandidateRequest req) {
        Candidate c = get(id);
        if (c.getVersion() != req.version()) {
            throw new ObjectOptimisticLockingFailureException(Candidate.class, id);
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
        Long oldDept = c.getDepartment() == null ? null : c.getDepartment().getId();
        if (!Objects.equals(oldDept, req.departmentId())) {
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
            repo.saveAndFlush(c);
        }
        return detail(id);
    }

    @Transactional
    public CandidateDetail updateStatus(Long id, StatusUpdateRequest req, String actor) {
        Candidate c = get(id);
        if (req.applicationStatus() == null && req.verificationStatus() == null) {
            throw ApiException.badRequest("Provide an application or verification status");
        }
        boolean changed = false;
        if (req.applicationStatus() != null && req.applicationStatus() != c.getApplicationStatus()) {
            audit.record("STATUS_CHANGED", "CANDIDATE", c.getId(), "Application status: " + c.getApplicationStatus().label
                    + " → " + req.applicationStatus().label + (StringUtils.hasText(req.note()) ? " - " + req.note().trim() : ""));
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
        if (StringUtils.hasText(req.note())) remarks.save(new HrRemark(c, actor, req.note().trim()));
        return detail(id);
    }

    @Transactional
    public RemarkResponse addRemark(Long id, String text, String actor) {
        Candidate c = get(id);
        HrRemark r = remarks.save(new HrRemark(c, actor, text.trim()));
        audit.record("REMARK_ADDED", "CANDIDATE", c.getId(), "HR remark added");
        return RemarkResponse.from(r);
    }

    /** Assigns the human-readable ID (JPM-YYYY-NNNNN) after the row has its database id. */
    public static void assignCode(Candidate c) {
        LocalDate d = c.getApplicationDate() == null ? LocalDate.now() : c.getApplicationDate();
        c.setCandidateCode(String.format("JPM-%d-%05d", d.getYear(), c.getId()));
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
