package com.jpm.hrms.communication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An approved template version. Rows are never edited after creation (only deactivated). */
@Entity
@Table(name = "message_templates")
public class MessageTemplate {

    public enum TemplateChannel { WHATSAPP, EMAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateChannel channel;
    private int version;
    private String subject;
    @Column(nullable = false)
    private String body;
    private boolean active;
    @Column(nullable = false)
    private String createdBy;
    private Instant createdAt;

    protected MessageTemplate() {
    }

    public MessageTemplate(TemplateChannel channel, int version, String subject, String body, String createdBy) {
        this.channel = channel;
        this.version = version;
        this.subject = subject;
        this.body = body;
        this.createdBy = createdBy;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public TemplateChannel getChannel() { return channel; }
    public int getVersion() { return version; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
