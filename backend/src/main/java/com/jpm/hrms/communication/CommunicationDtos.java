package com.jpm.hrms.communication;

import com.jpm.hrms.communication.Communication.CallOutcome;
import com.jpm.hrms.communication.Communication.Channel;
import com.jpm.hrms.communication.Communication.Status;
import com.jpm.hrms.communication.MessageTemplate.TemplateChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CommunicationDtos {
    private CommunicationDtos() {
    }

    public record SettingsResponse(String companyName, String defaultVenue, String locationLink, String hrSignature,
                                   String defaultCountryCode, String updatedBy, Instant updatedAt) {
        static SettingsResponse from(CommunicationSettings s) {
            return new SettingsResponse(s.getCompanyName(), s.getDefaultVenue(), s.getLocationLink(), s.getHrSignature(),
                    s.getDefaultCountryCode(), s.getUpdatedBy(), s.getUpdatedAt());
        }
    }

    public record SettingsRequest(@NotBlank @Size(max = 150) String companyName,
                                  @NotBlank @Size(max = 300) String defaultVenue,
                                  @Size(max = 500) @Pattern(regexp = "^(https?://\\S+)?$", message = "must be a link starting with https://")
                                  String locationLink,
                                  @NotBlank @Size(max = 500) String hrSignature,
                                  @NotBlank @Pattern(regexp = "^\\d{1,3}$", message = "must be 1-3 digits, e.g. 91") String defaultCountryCode) {
    }

    public record TemplateResponse(Long id, TemplateChannel channel, int version, String subject, String body, boolean active,
                                   String createdBy, Instant createdAt) {
        static TemplateResponse from(MessageTemplate t) {
            return new TemplateResponse(t.getId(), t.getChannel(), t.getVersion(), t.getSubject(), t.getBody(), t.isActive(),
                    t.getCreatedBy(), t.getCreatedAt());
        }
    }

    /** {@code purpose} defaults to the interview invitation when omitted. */
    public record TemplateRequest(TemplatePurpose purpose, @NotNull TemplateChannel channel, @Size(max = 300) String subject,
                                  @NotBlank @Size(max = 4000) String body) {
        public TemplatePurpose purposeOrDefault() {
            return purpose == null ? TemplatePurpose.INTERVIEW_INVITATION : purpose;
        }
    }

    public record EmailServiceStatus(boolean configured, String provider, String fromAddress, String fromName) {
    }

    /** The approved WhatsApp and email template of one purpose. */
    public record PurposeTemplates(TemplatePurpose purpose, String label, boolean needsInterview, List<String> required,
                                   TemplateResponse whatsapp, TemplateResponse email) {
    }

    public record TemplatesOverview(List<PurposeTemplates> purposes, SettingsResponse settings, Map<String, String> placeholders,
                                    List<String> interviewPlaceholders, EmailServiceStatus emailService) {
    }

    public record PreviewRequest(TemplatePurpose purpose, @NotNull TemplateChannel channel, @Size(max = 300) String subject,
                                 @NotBlank @Size(max = 4000) String body) {
        public TemplatePurpose purposeOrDefault() {
            return purpose == null ? TemplatePurpose.INTERVIEW_INVITATION : purpose;
        }
    }

    public record PreviewResponse(String subject, String body, List<String> problems, boolean sample) {
    }

    // ------------------------------------------------------------- per-candidate compose

    public record ContactInfo(String mobile, boolean mobileValid, String whatsappNumber, String mobileProblem,
                              String email, boolean emailValid, String emailProblem) {
    }

    public record InterviewSummary(Long id, Instant scheduledAt, String date, String time, String roundName,
                                   String interviewer, String status, String venue, String location) {
    }

    public record WhatsAppDraft(boolean ready, String message, String url, Integer templateVersion, List<String> problems) {
    }

    public record EmailDraft(boolean ready, String to, String subject, String body, Integer templateVersion,
                             List<String> problems, boolean serviceConfigured) {
    }

    public record ComposeResponse(TemplatePurpose purpose, String purposeLabel, Long candidateId, String candidateCode, String candidateName, String department,
                                  ContactInfo contact, InterviewSummary interview, WhatsAppDraft whatsapp, EmailDraft email) {
    }

    /** {@code purpose} defaults to the interview invitation when omitted. */
    public record ComposeRequest(Long interviewId, TemplatePurpose purpose) {
        public TemplatePurpose purposeOrDefault() {
            return purpose == null ? TemplatePurpose.INTERVIEW_INVITATION : purpose;
        }
    }

    public record CommunicationResponse(Long id, Long candidateId, Long interviewId, TemplatePurpose purpose, Channel channel, String recipient,
                                        String candidateName, Integer templateVersion, String subject, String content,
                                        Status status, String provider, String providerMessageId, String errorMessage,
                                        CallOutcome callOutcome, String notes, Long retryOfId, String initiatedBy,
                                        Instant createdAt, Instant updatedAt) {
        static CommunicationResponse from(Communication c) {
            return new CommunicationResponse(c.getId(), c.getCandidateId(), c.getInterviewId(), c.getPurpose(), c.getChannel(), c.getRecipient(),
                    c.getCandidateName(), c.getTemplateVersion(), c.getSubject(), c.getContent(), c.getStatus(), c.getProvider(),
                    c.getProviderMessageId(), c.getErrorMessage(), c.getCallOutcome(), c.getNotes(), c.getRetryOfId(),
                    c.getInitiatedBy(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record WhatsAppPrepared(CommunicationResponse communication, String url) {
    }

    public record CallStarted(CommunicationResponse communication, String telUri) {
    }

    public record CallOutcomeRequest(@NotNull CallOutcome outcome, @Size(max = 1000) String notes) {
    }

    public record TestEmailRequest(@NotBlank @jakarta.validation.constraints.Email @Size(max = 200) String to) {
    }

    public record TestEmailResult(boolean accepted, String messageId, String error) {
    }
}
