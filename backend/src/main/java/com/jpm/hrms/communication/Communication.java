package com.jpm.hrms.communication;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


import java.time.Instant;

/**
 * One call / WhatsApp / email action. Recipient, candidate name and content are snapshots, so the
 * record stays accurate if the candidate's details or the template change later.
 */
@Document("communications")
public class Communication implements SequencedEntity {

    public enum Channel { CALL, WHATSAPP, EMAIL }

    /**
     * Honest status model: a WhatsApp click-to-chat message is only ever PREPARED (or MARKED_SENT when
     * HR confirms they pressed send); email is ACCEPTED once the SMTP provider accepts it. DELIVERED is
     * reserved for providers that report delivery - plain SMTP does not.
     */
    public enum Status { CALL_INITIATED, PREPARED, MARKED_SENT, ACCEPTED, DELIVERED, FAILED }

    public enum CallOutcome { CONNECTED, NO_ANSWER, BUSY, SWITCHED_OFF, WRONG_NUMBER, CALL_BACK_LATER }

    @Id
    private Long id;
    private Long candidateId;
    private Long interviewId;
    private Channel channel;
    private String recipient;
    private String candidateName;
    private Long templateId;
    private Integer templateVersion;
    private String subject;
    private String content;
    private Status status;
    private String provider;
    private String providerMessageId;
    private String errorMessage;
    private CallOutcome callOutcome;
    private String notes;
    private Long retryOfId;
    private String initiatedBy;
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
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public Long getInterviewId() { return interviewId; }
    public void setInterviewId(Long interviewId) { this.interviewId = interviewId; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Integer getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(Integer templateVersion) { this.templateVersion = templateVersion; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public CallOutcome getCallOutcome() { return callOutcome; }
    public void setCallOutcome(CallOutcome callOutcome) { this.callOutcome = callOutcome; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getRetryOfId() { return retryOfId; }
    public void setRetryOfId(Long retryOfId) { this.retryOfId = retryOfId; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
