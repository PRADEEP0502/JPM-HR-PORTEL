package com.jpm.hrms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.stereotype.Component;

/**
 * Creates the indexes every collection relies on (idempotent: existing indexes are left as they are).
 * Kept in code rather than via annotations so they are reviewed like any other schema change.
 */
@Component
@Order(0)
public class MongoIndexes implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MongoIndexes.class);
    /** Case-insensitive comparison (e.g. "Admin" and "admin" are the same username). */
    private static final Collation CASE_INSENSITIVE = Collation.of("en").strength(Collation.ComparisonLevel.secondary());

    private final MongoOperations ops;

    public MongoIndexes(MongoOperations ops) {
        this.ops = ops;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAll();
    }

    public void ensureAll() {
        index("app_users", new Index().on("username", Direction.ASC).unique().collation(CASE_INSENSITIVE).named("uk_username"));
        index("departments", new Index().on("name", Direction.ASC).unique().collation(CASE_INSENSITIVE).named("uk_department_name"));

        // Candidate search, filters and sorting on the candidate list, dashboard and reports.
        index("candidates", new Index().on("candidateCode", Direction.ASC).unique().sparse().named("uk_candidate_code"));
        index("candidates", new Index().on("sourceRowKey", Direction.ASC).unique().sparse().named("uk_source_row"));
        index("candidates", new Index().on("fullName", Direction.ASC).named("idx_full_name"));
        index("candidates", new Index().on("email", Direction.ASC).named("idx_email"));
        index("candidates", new Index().on("mobile", Direction.ASC).named("idx_mobile"));
        index("candidates", new Index().on("departmentId", Direction.ASC).on("applicationStatus", Direction.ASC).named("idx_department_status"));
        index("candidates", new Index().on("applicationStatus", Direction.ASC).on("appliedAt", Direction.DESC).named("idx_status_applied"));
        index("candidates", new Index().on("interviewStatus", Direction.ASC).named("idx_interview_status"));
        index("candidates", new Index().on("verificationStatus", Direction.ASC).named("idx_verification"));
        index("candidates", new Index().on("applicationDate", Direction.ASC).named("idx_application_date"));
        index("candidates", new Index().on("appliedAt", Direction.DESC).named("idx_applied_at"));
        index("candidates", new Index().on("profileRating", Direction.DESC).named("idx_profile_rating"));
        index("candidates", new Index().on("source", Direction.ASC).named("idx_source"));
        index("candidates", new Index().on("createdAt", Direction.DESC).named("idx_created_at"));

        index("interviews", new Index().on("candidateId", Direction.ASC).on("scheduledAt", Direction.DESC).named("idx_candidate_time"));
        index("interviews", new Index().on("status", Direction.ASC).on("scheduledAt", Direction.ASC).named("idx_status_time"));
        index("interviews", new Index().on("interviewer", Direction.ASC).on("scheduledAt", Direction.ASC)
                .collation(CASE_INSENSITIVE).named("idx_interviewer_time"));

        index("hr_remarks", new Index().on("candidateId", Direction.ASC).on("createdAt", Direction.DESC).named("idx_candidate_time"));
        index("candidate_documents", new Index().on("candidateId", Direction.ASC).on("createdAt", Direction.DESC).named("idx_candidate_time"));
        index("communications", new Index().on("candidateId", Direction.ASC).on("createdAt", Direction.DESC).named("idx_candidate_time"));
        index("message_templates", new Index().on("channel", Direction.ASC).on("version", Direction.DESC).unique().named("uk_channel_version"));

        index("audit_logs", new Index().on("occurredAt", Direction.DESC).named("idx_occurred_at"));
        index("audit_logs", new Index().on("entityType", Direction.ASC).on("entityId", Direction.ASC).on("occurredAt", Direction.DESC).named("idx_entity"));
        index("audit_logs", new Index().on("action", Direction.ASC).on("occurredAt", Direction.DESC).named("idx_action"));
        index("sync_runs", new Index().on("startedAt", Direction.DESC).named("idx_started_at"));
        log.debug("MongoDB indexes verified");
    }

    private void index(String collection, Index index) {
        ops.indexOps(collection).createIndex(index);
    }
}
