package com.jpm.hrms.candidate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "hr_remarks")
public class HrRemark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String remark;

    private Instant createdAt;

    protected HrRemark() {
    }

    public HrRemark(Candidate candidate, String author, String remark) {
        this.candidate = candidate;
        this.author = author;
        this.remark = remark;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getAuthor() { return author; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
}
