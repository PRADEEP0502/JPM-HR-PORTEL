package com.jpm.hrms.candidate;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.ExperienceBucket;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.common.MongoText;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Builds the dynamic MongoDB filter for the candidate list / export. */
public final class CandidateSpecifications {
    private CandidateSpecifications() {
    }

    /** departmentId = 0 means "no department assigned"; several interview statuses are OR-ed. */
    public record Filter(String q, Long departmentId, ExperienceBucket experience, ApplicationStatus applicationStatus,
                         List<InterviewStatus> interviewStatuses, VerificationStatus verificationStatus,
                         RatingFilter rating) {
        public Filter(String q, Long departmentId, ExperienceBucket experience, ApplicationStatus applicationStatus,
                      List<InterviewStatus> interviewStatuses, VerificationStatus verificationStatus) {
            this(q, departmentId, experience, applicationStatus, interviewStatuses, verificationStatus, null);
        }
    }

    /** Profile Match Rating filter: "UNRATED", a single value "7", a range "7-10", or "7+" (7 and above). */
    public record RatingFilter(boolean unrated, int min, int max) {
        private static final Pattern RANGE = Pattern.compile("(\\d{1,2})(?:-(\\d{1,2})|(\\+))?");

        public static RatingFilter parse(String raw) {
            if (!StringUtils.hasText(raw)) return null;
            String v = raw.trim();
            if (v.equalsIgnoreCase("UNRATED")) return new RatingFilter(true, 0, 0);
            var m = RANGE.matcher(v);
            if (m.matches()) {
                int min = Integer.parseInt(m.group(1));
                int max = m.group(2) != null ? Integer.parseInt(m.group(2)) : m.group(3) != null ? 10 : min;
                if (min >= 1 && max <= 10 && min <= max) return new RatingFilter(false, min, max);
            }
            throw ApiException.badRequest("Rating filter must be UNRATED, a whole number 1-10, a range like 7-10, or 7+");
        }
    }

    public static Criteria matching(Filter f) {
        List<Criteria> cs = new ArrayList<>();
        if (StringUtils.hasText(f.q())) {
            String text = MongoText.contains(f.q());
            cs.add(new Criteria().orOperator(
                    Criteria.where("fullName").regex(text, "i"),
                    Criteria.where("candidateCode").regex(text, "i"),
                    Criteria.where("mobile").regex(text)));
        }
        if (f.departmentId() != null) {
            cs.add(Criteria.where("departmentId").is(f.departmentId() == 0 ? null : f.departmentId()));
        }
        if (f.experience() != null) {
            switch (f.experience()) {
                case UNKNOWN -> cs.add(Criteria.where("experienceYears").is(null));
                case FRESHER -> cs.add(Criteria.where("experienceYears").is(BigDecimal.ZERO));
                default -> {
                    Criteria exp = Criteria.where("experienceYears");
                    if (f.experience().minExclusive != null) exp = exp.gt(f.experience().minExclusive);
                    if (f.experience().maxInclusive != null) exp = exp.lte(f.experience().maxInclusive);
                    cs.add(exp);
                }
            }
        }
        if (f.applicationStatus() != null) cs.add(Criteria.where("applicationStatus").is(f.applicationStatus()));
        if (f.interviewStatuses() != null && !f.interviewStatuses().isEmpty()) {
            cs.add(Criteria.where("interviewStatus").in(f.interviewStatuses()));
        }
        if (f.verificationStatus() != null) cs.add(Criteria.where("verificationStatus").is(f.verificationStatus()));
        if (f.rating() != null) {
            cs.add(f.rating().unrated()
                    ? Criteria.where("profileRating").is(null)
                    : Criteria.where("profileRating").gte(f.rating().min()).lte(f.rating().max()));
        }
        return cs.isEmpty() ? new Criteria() : new Criteria().andOperator(cs);
    }
}
