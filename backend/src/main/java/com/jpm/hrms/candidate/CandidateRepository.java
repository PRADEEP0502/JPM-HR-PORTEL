package com.jpm.hrms.candidate;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.common.CriteriaRepository;
import org.springframework.data.mongodb.core.query.Criteria;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CandidateRepository extends CriteriaRepository<Candidate, Long>, CandidateRepositoryCustom {

    long countByDepartmentId(Long departmentId);

    long countByApplicationDate(LocalDate date);

    long countByApplicationStatus(ApplicationStatus status);

    long countByVerificationStatus(VerificationStatus status);

    long countBySource(CandidateSource source);

    boolean existsBySourceRowKey(String key);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByMobile(String mobile);

    List<Candidate> findTop8ByOrderByAppliedAtDesc();

    List<Candidate> findTop10ByCreatedAtAfterOrderByCreatedAtDesc(Instant after);

    long countByCreatedAtAfter(Instant after);

    List<Candidate> findBySource(CandidateSource source);

    List<Candidate> findByApplicationStatusInOrderByApplicationDateDesc(Collection<ApplicationStatus> statuses);

    List<Candidate> findByVerificationStatusOrderByApplicationDateDesc(VerificationStatus status);

    /** Position of a candidate within its application year (inclusive bounds); used for the YYYY-N candidate code. */
    default long countBySourceNotAndApplicationDateBetweenAndIdLessThanEqual(CandidateSource source, LocalDate from,
                                                                             LocalDate to, Long id) {
        return count(Criteria.where("source").ne(source).and("applicationDate").gte(from).lte(to).and("_id").lte(id));
    }
}
