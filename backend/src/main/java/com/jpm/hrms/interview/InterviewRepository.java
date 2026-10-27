package com.jpm.hrms.interview;

import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.common.CriteriaRepository;
import com.jpm.hrms.common.MongoText;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface InterviewRepository extends CriteriaRepository<Interview, Long> {

    List<Interview> findByCandidateIdOrderByScheduledAtDesc(Long candidateId);

    long countByStatusInAndScheduledAtAfter(Collection<InterviewStatus> statuses, Instant after);

    List<Interview> findTop6ByStatusInAndScheduledAtAfterOrderByScheduledAtAsc(Collection<InterviewStatus> statuses, Instant after);

    /** Interviews starting in [from, to). */
    default List<Interview> findByStatusInAndScheduledAtBetweenOrderByScheduledAtAsc(Collection<InterviewStatus> statuses,
                                                                                     Instant from, Instant to) {
        return findAll(Criteria.where("status").in(statuses).and("scheduledAt").gte(from).lt(to),
                Sort.by(Sort.Direction.ASC, "scheduledAt"));
    }

    void deleteByCandidateIdIn(Collection<Long> candidateIds);

    /** Active interviews for the same interviewer (case-insensitive) whose start falls inside a window (clash detection). */
    default List<Interview> findClashes(String interviewer, Collection<InterviewStatus> statuses,
                                        Instant windowStart, Instant windowEnd, long excludeId) {
        return findAll(Criteria.where("interviewer").regex(MongoText.exact(interviewer), "i")
                .and("status").in(statuses)
                .and("scheduledAt").gt(windowStart).lt(windowEnd)
                .and("_id").ne(excludeId), Sort.unsorted());
    }
}
