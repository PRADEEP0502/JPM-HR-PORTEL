package com.jpm.hrms.candidate;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.ExperienceBucket;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Builds the dynamic WHERE clause for the candidate list / export. */
public final class CandidateSpecifications {
    private CandidateSpecifications() {
    }

    /** departmentId = 0 means "no department assigned"; several interview statuses are OR-ed. */
    public record Filter(String q, Long departmentId, ExperienceBucket experience, ApplicationStatus applicationStatus,
                         List<InterviewStatus> interviewStatuses, VerificationStatus verificationStatus) {
    }

    public static Specification<Candidate> matching(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StringUtils.hasText(f.q())) {
                String like = "%" + f.q().trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(root.get("candidateCode")), like, '\\'),
                        cb.like(root.get("mobile"), like, '\\')));
            }
            if (f.departmentId() != null) {
                ps.add(f.departmentId() == 0
                        ? cb.isNull(root.get("department"))
                        : cb.equal(root.get("department").get("id"), f.departmentId()));
            }
            if (f.experience() != null) {
                var exp = root.<BigDecimal>get("experienceYears");
                switch (f.experience()) {
                    case UNKNOWN -> ps.add(cb.isNull(exp));
                    case FRESHER -> ps.add(cb.equal(exp, BigDecimal.ZERO));
                    default -> {
                        if (f.experience().minExclusive != null) ps.add(cb.greaterThan(exp, f.experience().minExclusive));
                        if (f.experience().maxInclusive != null) ps.add(cb.lessThanOrEqualTo(exp, f.experience().maxInclusive));
                    }
                }
            }
            if (f.applicationStatus() != null) ps.add(cb.equal(root.get("applicationStatus"), f.applicationStatus()));
            if (f.interviewStatuses() != null && !f.interviewStatuses().isEmpty()) {
                ps.add(root.get("interviewStatus").in(f.interviewStatuses()));
            }
            if (f.verificationStatus() != null) ps.add(cb.equal(root.get("verificationStatus"), f.verificationStatus()));
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }
}
