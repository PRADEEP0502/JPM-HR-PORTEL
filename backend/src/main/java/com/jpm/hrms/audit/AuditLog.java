package com.jpm.hrms.audit;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


import java.time.Instant;

@Document("audit_logs")
public class AuditLog implements SequencedEntity {
    @Id
    private Long id;
    private Instant occurredAt;
    private String actor;
    private String action;
    private String entityType;
    private Long entityId;
    private String details;

    protected AuditLog() {
    }

    public AuditLog(Instant occurredAt, String actor, String action, String entityType, Long entityId, String details) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    public Long getId() { return id; }
    @Override
    public void assignId(Long id) { this.id = id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getDetails() { return details; }
}
