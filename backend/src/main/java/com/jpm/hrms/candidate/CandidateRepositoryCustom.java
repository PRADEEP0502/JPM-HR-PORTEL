package com.jpm.hrms.candidate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Aggregation queries for the dashboard and computed sort orders (implemented in {@link CandidateRepositoryCustomImpl}). */
public interface CandidateRepositoryCustom {

    /** Rows: [ApplicationStatus, Long count] */
    List<Object[]> countGroupedByApplicationStatus();

    /** Rows: [InterviewStatus, Long count] */
    List<Object[]> countGroupedByInterviewStatus();

    List<BigDecimal> findAllExperienceYears();

    List<LocalDate> findApplicationDatesSince(LocalDate from);

    /** Rows: [departmentId (nullable), ApplicationStatus, VerificationStatus, Long count] */
    List<Object[]> countGroupedByDepartmentAndStatus();

    /** Rows: [departmentId (nullable), Long count] */
    List<Object[]> countOnDateByDepartment(LocalDate today);

    /**
     * Runs match, then a computed sort key, then sort (and paging when {@code pageable} is not null). Used for
     * orderings a plain field sort cannot express, such as "unrated last" or department name order.
     */
    Page<Candidate> findAllOrdered(Criteria criteria, AggregationOperation sortKey, Sort sort, Pageable pageable);

    List<Candidate> findAllOrdered(Criteria criteria, AggregationOperation sortKey, Sort sort);
}
