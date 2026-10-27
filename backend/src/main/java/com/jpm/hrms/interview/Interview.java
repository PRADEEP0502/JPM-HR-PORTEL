package com.jpm.hrms.interview;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;

import java.time.Instant;

@Document("interviews")
public class Interview implements SequencedEntity {

    public enum Mode { IN_PERSON, VIDEO, PHONE }

    /** Interviewer's overall recommendation; the hiring decision stays with HR. */
    public enum Recommendation { STRONGLY_RECOMMEND, RECOMMEND, NEUTRAL, NOT_RECOMMENDED }

    @Id
    private Long id;

    private Long candidateId;

    private Instant scheduledAt;
    private int durationMinutes;
    private String roundName;
    private String interviewer;
    private Mode mode;
    private String location;
    private InterviewStatus status;
    private String remarks;
    private String createdBy;
    /** Username of the Interviewer account this interview is assigned to (null when the interviewer has no account). */
    private String interviewerUsername;
    private Recommendation recommendation;
    private String feedback;
    private String feedbackBy;
    private Instant feedbackAt;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

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
    public Long getCandidateId() { return candidateId; }
    public void setCandidate(Candidate candidate) { this.candidateId = candidate.getId(); }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public String getInterviewer() { return interviewer; }
    public void setInterviewer(String interviewer) { this.interviewer = interviewer; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public InterviewStatus getStatus() { return status; }
    public void setStatus(InterviewStatus status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getInterviewerUsername() { return interviewerUsername; }
    public void setInterviewerUsername(String interviewerUsername) { this.interviewerUsername = interviewerUsername; }
    public Recommendation getRecommendation() { return recommendation; }
    public void setRecommendation(Recommendation recommendation) { this.recommendation = recommendation; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public String getFeedbackBy() { return feedbackBy; }
    public void setFeedbackBy(String feedbackBy) { this.feedbackBy = feedbackBy; }
    public Instant getFeedbackAt() { return feedbackAt; }
    public void setFeedbackAt(Instant feedbackAt) { this.feedbackAt = feedbackAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
