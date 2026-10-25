package com.jpm.hrms.candidate;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CandidateRepository extends JpaRepository<Candidate, Long>, JpaSpecificationExecutor<Candidate> {

    @Override
    @EntityGraph(attributePaths = "department")
    Page<Candidate> findAll(Specification<Candidate> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "department")
    List<Candidate> findAll(Specification<Candidate> spec, Sort sort);

    long countByDepartmentId(Long departmentId);

    long countByApplicationDate(LocalDate date);

    long countByApplicationStatus(ApplicationStatus status);

    long countByVerificationStatus(VerificationStatus status);

    long countBySource(CandidateSource source);

    boolean existsBySourceRowKey(String key);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByMobile(String mobile);

    @EntityGraph(attributePaths = "department")
    List<Candidate> findTop8ByOrderByAppliedAtDesc();

    List<Candidate> findTop10ByCreatedAtAfterOrderByCreatedAtDesc(Instant after);

    long countByCreatedAtAfter(Instant after);

    List<Candidate> findBySource(CandidateSource source);

    @Query("select c.applicationStatus, count(c) from Candidate c group by c.applicationStatus")
    List<Object[]> countGroupedByApplicationStatus();

    @Query("select c.interviewStatus, count(c) from Candidate c group by c.interviewStatus")
    List<Object[]> countGroupedByInterviewStatus();

    @Query("select c.experienceYears from Candidate c")
    List<BigDecimal> findAllExperienceYears();

    @Query("select c.applicationDate from Candidate c where c.applicationDate >= :from")
    List<LocalDate> findApplicationDatesSince(@Param("from") LocalDate from);

    /** Rows: [departmentId (nullable), applicationStatus, verificationStatus, count] */
    @Query("""
            select d.id, c.applicationStatus, c.verificationStatus, count(c)
            from Candidate c left join c.department d
            group by d.id, c.applicationStatus, c.verificationStatus
            """)
    List<Object[]> countGroupedByDepartmentAndStatus();

    @Query("""
            select d.id, count(c) from Candidate c left join c.department d
            where c.applicationDate = :today group by d.id
            """)
    List<Object[]> countOnDateByDepartment(@Param("today") LocalDate today);

    @EntityGraph(attributePaths = "department")
    List<Candidate> findByApplicationStatusInOrderByApplicationDateDesc(Collection<ApplicationStatus> statuses);

    @EntityGraph(attributePaths = "department")
    List<Candidate> findByVerificationStatusOrderByApplicationDateDesc(VerificationStatus status);
}
