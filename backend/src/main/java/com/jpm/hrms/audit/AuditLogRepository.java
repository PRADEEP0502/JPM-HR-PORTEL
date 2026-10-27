package com.jpm.hrms.audit;

import com.jpm.hrms.common.CriteriaRepository;

import java.util.List;

public interface AuditLogRepository extends CriteriaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, Long entityId);
}
