package com.jpm.hrms.candidate;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.ExperienceBucket;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.candidate.CandidateSpecifications.ArchiveScope;
import com.jpm.hrms.candidate.CandidateSpecifications.Filter;
import com.jpm.hrms.candidate.CandidateSpecifications.RatingFilter;
import com.jpm.hrms.common.ApiException;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Query-string filters shared by the candidate list and its export, e.g.
 * {@code ?departmentId=3&position=Helper&applicationStatus=NEW,UNDER_REVIEW&rating=7+&appliedFrom=2026-01-01&archive=ALL}.
 */
public record CandidateQuery(String q, Long departmentId, String position, ExperienceBucket experience,
                             List<ApplicationStatus> applicationStatus, List<InterviewStatus> interviewStatus,
                             VerificationStatus verificationStatus, String rating,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedFrom,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedTo,
                             ArchiveScope archive) {

    public Filter toFilter() {
        if (appliedFrom != null && appliedTo != null && appliedFrom.isAfter(appliedTo)) {
            throw ApiException.badRequest("'Applied from' must be on or before 'Applied to'");
        }
        return new Filter(q, departmentId, position, experience, applicationStatus, interviewStatus, verificationStatus,
                RatingFilter.parse(rating), appliedFrom, appliedTo, archive == null ? ArchiveScope.ACTIVE : archive);
    }
}
