package com.jpm.hrms.candidate;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One Profile Match Rating change (internal to HR). Never edited or deleted. */
@Document("profile_rating_history")
public class RatingChange implements SequencedEntity {
    @Id
    private Long id;
    private Long candidateId;
    private Integer fromRating;
    private Integer toRating;
    private String remark;
    private String ratedBy;
    private Instant ratedAt;

    protected RatingChange() {
    }

    public RatingChange(Long candidateId, Integer fromRating, Integer toRating, String remark, String ratedBy) {
        this.candidateId = candidateId;
        this.fromRating = fromRating;
        this.toRating = toRating;
        this.remark = remark;
        this.ratedBy = ratedBy;
        this.ratedAt = Instant.now();
    }

    public Long getId() { return id; }
    @Override
    public void assignId(Long id) { this.id = id; }
    public Long getCandidateId() { return candidateId; }
    public Integer getFromRating() { return fromRating; }
    public Integer getToRating() { return toRating; }
    public String getRemark() { return remark; }
    public String getRatedBy() { return ratedBy; }
    public Instant getRatedAt() { return ratedAt; }
}
