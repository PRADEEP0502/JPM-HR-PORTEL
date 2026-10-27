package com.jpm.hrms.department;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


import java.time.Instant;

@Document("departments")
public class Department implements SequencedEntity {
    @Id
    private Long id;

    private String name;

    private String description;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    protected Department() {
    }

    public Department(String name, String description) {
        this.name = name;
        this.description = description;
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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
