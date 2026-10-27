package com.jpm.hrms.communication;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


import java.time.Instant;

/** An approved template version. Rows are never edited after creation (only deactivated). */
@Document("message_templates")
public class MessageTemplate implements SequencedEntity {

    public enum TemplateChannel { WHATSAPP, EMAIL }

    @Id
    private Long id;
    /** Older templates without a purpose are set to INTERVIEW_INVITATION at startup (CommunicationService.ensureDefaults). */
    private TemplatePurpose purpose;
    private TemplateChannel channel;
    private int version;
    private String subject;
    private String body;
    private boolean active;
    private String createdBy;
    private Instant createdAt;

    protected MessageTemplate() {
    }

    public MessageTemplate(TemplatePurpose purpose, TemplateChannel channel, int version, String subject, String body, String createdBy) {
        this.purpose = purpose;
        this.channel = channel;
        this.version = version;
        this.subject = subject;
        this.body = body;
        this.createdBy = createdBy;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    @Override
    public void assignId(Long id) { this.id = id; }
    public TemplatePurpose getPurpose() { return purpose == null ? TemplatePurpose.INTERVIEW_INVITATION : purpose; }
    public TemplateChannel getChannel() { return channel; }
    public int getVersion() { return version; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
