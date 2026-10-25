package com.jpm.hrms.interview;

import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long>, JpaSpecificationExecutor<Interview> {

    @Override
    @EntityGraph(attributePaths = {"candidate", "candidate.department"})
    Page<Interview> findAll(Specification<Interview> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"candidate", "candidate.department"})
    List<Interview> findAll(Specification<Interview> spec, Sort sort);

    List<Interview> findByCandidateIdOrderByScheduledAtDesc(Long candidateId);

    long countByStatusInAndScheduledAtAfter(Collection<InterviewStatus> statuses, Instant after);

    @EntityGraph(attributePaths = {"candidate", "candidate.department"})
    List<Interview> findTop6ByStatusInAndScheduledAtAfterOrderByScheduledAtAsc(Collection<InterviewStatus> statuses, Instant after);

    @EntityGraph(attributePaths = {"candidate"})
    List<Interview> findByStatusInAndScheduledAtBetweenOrderByScheduledAtAsc(Collection<InterviewStatus> statuses, Instant from, Instant to);

    @Query("select i.status, count(i) from Interview i group by i.status")
    List<Object[]> countGroupedByStatus();

    /** Active interviews for the same interviewer whose start falls inside a window (for clash detection). */
    @Query("""
            select i from Interview i
            where lower(i.interviewer) = lower(:interviewer)
              and i.status in :statuses
              and i.scheduledAt > :windowStart and i.scheduledAt < :windowEnd
              and i.id <> :excludeId
            """)
    List<Interview> findClashes(@Param("interviewer") String interviewer,
                                @Param("statuses") Collection<InterviewStatus> statuses,
                                @Param("windowStart") Instant windowStart, @Param("windowEnd") Instant windowEnd,
                                @Param("excludeId") long excludeId);
}
