package com.jpm.hrms.requirement;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** How many people a department (or one sub-work in it) needs. No position = the whole department. */
@Document("hiring_requirements")
public class HiringRequirement implements SequencedEntity {
    @Id
    private Long id;
    private Long departmentId;
    private String position;
    private int needed;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    protected HiringRequirement() {
    }

    public HiringRequirement(Long departmentId, String position, int needed, String createdBy) {
        this.departmentId = departmentId;
        this.position = position;
        this.needed = needed;
        this.createdBy = createdBy;
    }

    @Override
    public void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @Override
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    @Override
    public void assignId(Long id) { this.id = id; }
    public Long getDepartmentId() { return departmentId; }
    public String getPosition() { return position; }
    public int getNeeded() { return needed; }
    public void setNeeded(int needed) { this.needed = needed; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
