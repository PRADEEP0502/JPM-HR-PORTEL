package com.jpm.hrms.candidate;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


import java.time.Instant;

@Document("hr_remarks")
public class HrRemark implements SequencedEntity {
    @Id
    private Long id;

    private Long candidateId;

    private String author;

    private String remark;

    private Instant createdAt;

    protected HrRemark() {
    }

    public HrRemark(Candidate candidate, String author, String remark) {
        this.candidateId = candidate.getId();
        this.author = author;
        this.remark = remark;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    @Override
    public void assignId(Long id) { this.id = id; }
    public String getAuthor() { return author; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
}
