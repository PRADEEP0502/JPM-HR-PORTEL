package com.jpm.hrms.communication;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.communication.Communication.Channel;
import com.jpm.hrms.communication.Communication.Status;
import com.jpm.hrms.communication.CommunicationDtos.CallStarted;
import com.jpm.hrms.communication.CommunicationDtos.CommunicationResponse;
import com.jpm.hrms.communication.CommunicationDtos.ComposeResponse;
import com.jpm.hrms.communication.CommunicationDtos.ContactInfo;
import com.jpm.hrms.communication.CommunicationDtos.EmailDraft;
import com.jpm.hrms.communication.CommunicationDtos.EmailServiceStatus;
import com.jpm.hrms.communication.CommunicationDtos.InterviewSummary;
import com.jpm.hrms.communication.CommunicationDtos.PreviewResponse;
import com.jpm.hrms.communication.CommunicationDtos.SettingsRequest;
import com.jpm.hrms.communication.CommunicationDtos.SettingsResponse;
import com.jpm.hrms.communication.CommunicationDtos.TemplateRequest;
import com.jpm.hrms.communication.CommunicationDtos.TemplateResponse;
import com.jpm.hrms.communication.CommunicationDtos.TemplatesOverview;
import com.jpm.hrms.communication.CommunicationDtos.TestEmailResult;
import com.jpm.hrms.communication.CommunicationDtos.WhatsAppDraft;
import com.jpm.hrms.communication.CommunicationDtos.WhatsAppPrepared;
import com.jpm.hrms.communication.ContactValidator.EmailCheck;
import com.jpm.hrms.communication.ContactValidator.PhoneCheck;
import com.jpm.hrms.communication.MessageTemplate.TemplateChannel;
import com.jpm.hrms.communication.TemplateRenderer.MissingValuesException;
import com.jpm.hrms.interview.Interview;
import com.jpm.hrms.interview.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class CommunicationService {

    /** Approved default wording (the company name, venue and signature come from settings). */
    static final String DEFAULT_EMAIL_SUBJECT = "Interview Invitation – JPM";
    static final String DEFAULT_EMAIL_BODY = """
            Dear {{CandidateName}},

            Greetings from {{CompanyName}}!

            Your interview has been scheduled for the {{Department}} department.

            Interview Details

            Date: {{InterviewDate}}
            Time: {{InterviewTime}}
            Venue: {{Venue}}
            Department: {{Department}}

            Please confirm your availability for the interview.

            Best regards,
            {{HRSignature}}""";
    /** Same wording; the heading is bold using WhatsApp's *text* formatting. */
    static final String DEFAULT_WHATSAPP_BODY = DEFAULT_EMAIL_BODY.replace("\nInterview Details\n", "\n*Interview Details*\n");

    private static final Map<String, String> FRIENDLY = Map.of(
            TemplateRenderer.CANDIDATE_NAME, "candidate name",
            TemplateRenderer.DEPARTMENT, "department (assign one on the candidate's profile)",
            TemplateRenderer.INTERVIEW_DATE, "interview date",
            TemplateRenderer.INTERVIEW_TIME, "interview time",
            TemplateRenderer.VENUE, "interview venue (Settings → Communication)",
            TemplateRenderer.COMPANY_NAME, "company name (Settings → Communication)",
            TemplateRenderer.HR_SIGNATURE, "HR signature (Settings → Communication)",
            TemplateRenderer.INTERVIEW_ROUND, "interview round");

    private final CommunicationSettingsRepository settingsRepo;
    private final MessageTemplateRepository templates;
    private final CommunicationRepository communications;
    private final CandidateRepository candidates;
    private final InterviewRepository interviews;
    private final EmailSender email;
    private final AuditService audit;
    private final Clock clock;
    private final DateTimeFormatter dateFmt;
    private final DateTimeFormatter timeFmt;
    private final ZoneId zone;

    public CommunicationService(CommunicationSettingsRepository settingsRepo, MessageTemplateRepository templates,
                                CommunicationRepository communications, CandidateRepository candidates,
                                InterviewRepository interviews, EmailSender email, AuditService audit, Clock clock,
                                ZoneId businessZone) {
        this.settingsRepo = settingsRepo;
        this.templates = templates;
        this.communications = communications;
        this.candidates = candidates;
        this.interviews = interviews;
        this.email = email;
        this.audit = audit;
        this.clock = clock;
        this.zone = businessZone;
        this.dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH).withZone(businessZone);
        this.timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).withZone(businessZone);
    }

    // ================================================================ settings & templates

    /** Creates the settings row and version 1 of each template on first start. */
    @Transactional
    public void ensureDefaults() {
        if (!settingsRepo.existsById(CommunicationSettings.SINGLETON_ID)) {
            CommunicationSettings s = new CommunicationSettings();
            s.setUpdatedBy(AuditService.SYSTEM_ACTOR);
            settingsRepo.save(s);
        }
        if (templates.maxVersion(TemplateChannel.WHATSAPP) == 0) {
            templates.save(new MessageTemplate(TemplateChannel.WHATSAPP, 1, null, DEFAULT_WHATSAPP_BODY, AuditService.SYSTEM_ACTOR));
        }
        if (templates.maxVersion(TemplateChannel.EMAIL) == 0) {
            templates.save(new MessageTemplate(TemplateChannel.EMAIL, 1, DEFAULT_EMAIL_SUBJECT, DEFAULT_EMAIL_BODY, AuditService.SYSTEM_ACTOR));
        }
    }

    CommunicationSettings settings() {
        return settingsRepo.findById(CommunicationSettings.SINGLETON_ID)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Communication settings are not initialised"));
    }

    MessageTemplate activeTemplate(TemplateChannel channel) {
        return templates.findFirstByChannelAndActiveTrueOrderByVersionDesc(channel)
                .orElseThrow(() -> ApiException.badRequest("No approved " + channel.name().toLowerCase() + " template exists"));
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return SettingsResponse.from(settings());
    }

    @Transactional
    public SettingsResponse updateSettings(SettingsRequest req, String actor) {
        CommunicationSettings s = settings();
        s.setCompanyName(req.companyName().trim());
        s.setDefaultVenue(req.defaultVenue().trim());
        s.setHrSignature(req.hrSignature().trim().replace("\r\n", "\n"));
        s.setDefaultCountryCode(req.defaultCountryCode().trim());
        s.setUpdatedBy(actor);
        settingsRepo.saveAndFlush(s);
        audit.record("COMM_SETTINGS_UPDATED", "SETTINGS", s.getId(),
                "Company \"" + s.getCompanyName() + "\", venue \"" + s.getDefaultVenue() + "\", country code +" + s.getDefaultCountryCode());
        return SettingsResponse.from(s);
    }

    @Transactional(readOnly = true)
    public TemplatesOverview overview() {
        return new TemplatesOverview(TemplateResponse.from(activeTemplate(TemplateChannel.WHATSAPP)),
                TemplateResponse.from(activeTemplate(TemplateChannel.EMAIL)), SettingsResponse.from(settings()),
                TemplateRenderer.PLACEHOLDERS, TemplateRenderer.REQUIRED, emailStatus());
    }

    public EmailServiceStatus emailStatus() {
        return new EmailServiceStatus(email.isConfigured(), email.providerName(), email.fromAddress(), email.fromName());
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> history(TemplateChannel channel) {
        return templates.findByChannelOrderByVersionDesc(channel).stream().map(TemplateResponse::from).toList();
    }

    /** Saves a new approved version. Earlier versions stay in the database for the communication history. */
    @Transactional
    public TemplateResponse saveTemplate(TemplateRequest req, String actor) {
        String subject = req.channel() == TemplateChannel.EMAIL ? trimToNull(req.subject()) : null;
        if (req.channel() == TemplateChannel.EMAIL && subject == null) throw ApiException.badRequest("Email subject is required");
        String body = req.body().replace("\r\n", "\n").strip();
        List<String> problems = TemplateRenderer.validate(subject, body);
        if (!problems.isEmpty()) throw ApiException.badRequest(String.join("; ", problems));
        MessageTemplate current = activeTemplate(req.channel());
        if (current.getBody().equals(body) && Objects.equals(current.getSubject(), subject)) {
            throw ApiException.badRequest("No changes to save - this is already the approved template");
        }
        current.setActive(false);
        templates.saveAndFlush(current);
        MessageTemplate next = templates.save(new MessageTemplate(req.channel(), templates.maxVersion(req.channel()) + 1, subject, body, actor));
        audit.record("TEMPLATE_UPDATED", "TEMPLATE", next.getId(),
                req.channel() + " template version " + next.getVersion() + " approved (replaces version " + current.getVersion() + ")");
        return TemplateResponse.from(next);
    }

    /** Renders a draft with clearly-marked sample data so admins can review wording before approving it. */
    @Transactional(readOnly = true)
    public PreviewResponse previewDraft(TemplateChannel channel, String subject, String body) {
        List<String> problems = new ArrayList<>(TemplateRenderer.validate(channel == TemplateChannel.EMAIL ? subject : null, body));
        Map<String, String> values = baseValues(settings());
        LocalDate nextMonday = LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant sampleTime = nextMonday.atTime(10, 30).atZone(zone).toInstant();
        values.put(TemplateRenderer.CANDIDATE_NAME, "Priya Sharma");
        values.put(TemplateRenderer.DEPARTMENT, "Engineering");
        values.put(TemplateRenderer.INTERVIEW_DATE, dateFmt.format(sampleTime));
        values.put(TemplateRenderer.INTERVIEW_TIME, timeFmt.format(sampleTime));
        values.put(TemplateRenderer.INTERVIEW_ROUND, "Technical Round");
        String renderedSubject = null;
        String renderedBody = null;
        if (problems.isEmpty()) {
            try {
                renderedSubject = channel == TemplateChannel.EMAIL ? TemplateRenderer.render(subject, values) : null;
                renderedBody = TemplateRenderer.render(body.replace("\r\n", "\n").strip(), values);
            } catch (MissingValuesException e) {
                problems.add("Missing: " + friendly(e.getMissing()));
            }
        }
        return new PreviewResponse(renderedSubject, renderedBody, problems, true);
    }

    // ================================================================ compose for a candidate

    private record Context(Candidate candidate, Interview interview, CommunicationSettings settings, PhoneCheck phone,
                           EmailCheck email, List<String> interviewProblems, Map<String, String> values) {
    }

    private Context context(Long candidateId, Long interviewId) {
        Candidate c = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Candidate"));
        CommunicationSettings s = settings();
        Instant now = clock.instant();
        Interview interview;
        List<String> problems = new ArrayList<>();
        if (interviewId != null) {
            interview = interviews.findById(interviewId).orElseThrow(() -> ApiException.notFound("Interview"));
            if (!interview.getCandidate().getId().equals(c.getId())) throw ApiException.badRequest("Interview does not belong to this candidate");
        } else {
            interview = interviews.findByCandidateIdOrderByScheduledAtDesc(c.getId()).stream()
                    .filter(i -> i.getStatus().isActive() && i.getScheduledAt().isAfter(now))
                    .min(Comparator.comparing(Interview::getScheduledAt)).orElse(null);
        }
        if (interview == null) {
            problems.add("No upcoming interview is scheduled for this candidate - schedule one first");
        } else if (!interview.getStatus().isActive()) {
            problems.add("This interview is " + interview.getStatus().label.toLowerCase() + " - invitations are only sent for scheduled interviews");
        } else if (!interview.getScheduledAt().isAfter(now)) {
            problems.add("This interview's time has already passed - reschedule it before sending an invitation");
        }
        Map<String, String> values = baseValues(s);
        values.put(TemplateRenderer.CANDIDATE_NAME, c.getFullName());
        values.put(TemplateRenderer.DEPARTMENT, c.getDepartment() != null ? c.getDepartment().getName() : c.getDepartmentRaw());
        if (interview != null) {
            values.put(TemplateRenderer.INTERVIEW_DATE, dateFmt.format(interview.getScheduledAt()));
            values.put(TemplateRenderer.INTERVIEW_TIME, timeFmt.format(interview.getScheduledAt()));
            values.put(TemplateRenderer.INTERVIEW_ROUND, interview.getRoundName());
        }
        return new Context(c, interview, s, ContactValidator.checkPhone(c.getMobile(), s.getDefaultCountryCode()),
                ContactValidator.checkEmail(c.getEmail()), problems, values);
    }

    private Map<String, String> baseValues(CommunicationSettings s) {
        Map<String, String> v = new HashMap<>();
        v.put(TemplateRenderer.COMPANY_NAME, s.getCompanyName());
        v.put(TemplateRenderer.VENUE, s.getDefaultVenue());
        v.put(TemplateRenderer.HR_SIGNATURE, s.getHrSignature());
        return v;
    }

    private record Rendered(String subject, String body, List<String> problems) {
    }

    private Rendered render(MessageTemplate t, Context ctx) {
        List<String> problems = new ArrayList<>(ctx.interviewProblems());
        if (!problems.isEmpty()) return new Rendered(null, null, problems);
        try {
            String subject = t.getSubject() == null ? null : TemplateRenderer.render(t.getSubject(), ctx.values());
            return new Rendered(subject, TemplateRenderer.render(t.getBody(), ctx.values()), problems);
        } catch (MissingValuesException e) {
            problems.add("Missing " + friendly(e.getMissing()));
            return new Rendered(null, null, problems);
        }
    }

    @Transactional(readOnly = true)
    public ComposeResponse compose(Long candidateId, Long interviewId) {
        Context ctx = context(candidateId, interviewId);
        MessageTemplate wa = activeTemplate(TemplateChannel.WHATSAPP);
        MessageTemplate em = activeTemplate(TemplateChannel.EMAIL);

        Rendered waText = render(wa, ctx);
        List<String> waProblems = new ArrayList<>(waText.problems());
        if (!ctx.phone().valid()) waProblems.add(0, ctx.phone().reason());
        boolean waReady = waProblems.isEmpty();
        String url = waReady ? ContactValidator.whatsAppUrl(ctx.phone().international(), waText.body()) : null;

        Rendered emText = render(em, ctx);
        List<String> emProblems = new ArrayList<>(emText.problems());
        if (!ctx.email().valid()) emProblems.add(0, ctx.email().reason());
        boolean configured = email.isConfigured();

        Candidate c = ctx.candidate();
        Interview i = ctx.interview();
        InterviewSummary summary = i == null ? null : new InterviewSummary(i.getId(), i.getScheduledAt(), dateFmt.format(i.getScheduledAt()),
                timeFmt.format(i.getScheduledAt()), i.getRoundName(), i.getInterviewer(), i.getStatus().name(),
                ctx.settings().getDefaultVenue(), i.getLocation());
        return new ComposeResponse(c.getId(), c.getCandidateCode(), c.getFullName(),
                c.getDepartment() != null ? c.getDepartment().getName() : c.getDepartmentRaw(),
                new ContactInfo(c.getMobile(), ctx.phone().valid(), ctx.phone().display(), ctx.phone().reason(),
                        c.getEmail(), ctx.email().valid(), ctx.email().reason()),
                summary,
                new WhatsAppDraft(waReady, waText.body(), url, wa.getVersion(), waProblems),
                new EmailDraft(emProblems.isEmpty(), ctx.email().address(), emText.subject(), emText.body(), em.getVersion(),
                        emProblems, configured));
    }

    // ================================================================ actions

    /** Records the prepared WhatsApp message and returns the click-to-chat link. Nothing is sent by the server. */
    @Transactional
    public WhatsAppPrepared prepareWhatsApp(Long candidateId, Long interviewId, String actor) {
        Context ctx = context(candidateId, interviewId);
        if (!ctx.phone().valid()) throw ApiException.badRequest(ctx.phone().reason());
        MessageTemplate t = activeTemplate(TemplateChannel.WHATSAPP);
        Rendered r = render(t, ctx);
        if (!r.problems().isEmpty()) throw ApiException.badRequest(String.join("; ", r.problems()));
        Communication comm = base(ctx, Channel.WHATSAPP, ctx.phone().display(), actor);
        comm.setTemplateId(t.getId());
        comm.setTemplateVersion(t.getVersion());
        comm.setContent(r.body());
        comm.setStatus(Status.PREPARED);
        comm.setProvider("WhatsApp click-to-chat");
        communications.save(comm);
        audit.record("WHATSAPP_PREPARED", "CANDIDATE", ctx.candidate().getId(),
                "Interview invitation prepared for WhatsApp (template v" + t.getVersion() + ") - HR to review and send");
        return new WhatsAppPrepared(CommunicationResponse.from(comm), ContactValidator.whatsAppUrl(ctx.phone().international(), r.body()));
    }

    /** Sends the interview invitation email. The record is kept whether the provider accepts or rejects it. */
    @Transactional(noRollbackFor = ApiException.class)
    public CommunicationResponse sendEmail(Long candidateId, Long interviewId, String actor) {
        Context ctx = context(candidateId, interviewId);
        if (!ctx.email().valid()) throw ApiException.badRequest(ctx.email().reason());
        MessageTemplate t = activeTemplate(TemplateChannel.EMAIL);
        Rendered r = render(t, ctx);
        if (!r.problems().isEmpty()) throw ApiException.badRequest(String.join("; ", r.problems()));
        if (!email.isConfigured()) {
            throw ApiException.badRequest("Email service is not configured on the server - ask an administrator to set the MAIL_* settings");
        }
        Communication comm = base(ctx, Channel.EMAIL, ctx.email().address(), actor);
        comm.setTemplateId(t.getId());
        comm.setTemplateVersion(t.getVersion());
        comm.setSubject(r.subject());
        comm.setContent(r.body());
        return deliver(comm);
    }

    /** Re-sends the exact stored content of a failed email as a new attempt (the failed record is kept). */
    @Transactional(noRollbackFor = ApiException.class)
    public CommunicationResponse retry(Long communicationId, String actor) {
        Communication failed = communications.findById(communicationId).orElseThrow(() -> ApiException.notFound("Communication"));
        if (failed.getChannel() != Channel.EMAIL || failed.getStatus() != Status.FAILED) {
            throw ApiException.badRequest("Only failed emails can be retried");
        }
        if (!email.isConfigured()) throw ApiException.badRequest("Email service is not configured on the server");
        Communication comm = new Communication();
        comm.setCandidateId(failed.getCandidateId());
        comm.setInterviewId(failed.getInterviewId());
        comm.setChannel(Channel.EMAIL);
        comm.setRecipient(failed.getRecipient());
        comm.setCandidateName(failed.getCandidateName());
        comm.setTemplateId(failed.getTemplateId());
        comm.setTemplateVersion(failed.getTemplateVersion());
        comm.setSubject(failed.getSubject());
        comm.setContent(failed.getContent());
        comm.setRetryOfId(failed.getId());
        comm.setInitiatedBy(actor);
        return deliver(comm);
    }

    private CommunicationResponse deliver(Communication comm) {
        comm.setStatus(Status.PREPARED);
        comm.setProvider(email.providerName());
        communications.saveAndFlush(comm);
        try {
            String messageId = email.send(comm.getRecipient(), comm.getSubject(), comm.getContent());
            comm.setStatus(Status.ACCEPTED);
            comm.setProviderMessageId(messageId);
            comm.setErrorMessage(null);
            audit.record("EMAIL_ACCEPTED", "CANDIDATE", comm.getCandidateId(),
                    "Interview invitation email accepted by " + comm.getProvider() + " (template v" + comm.getTemplateVersion() + ")"
                            + (comm.getRetryOfId() != null ? " - retry of #" + comm.getRetryOfId() : ""));
        } catch (EmailSender.EmailException e) {
            comm.setStatus(Status.FAILED);
            comm.setErrorMessage(e.getMessage());
            audit.record("EMAIL_FAILED", "CANDIDATE", comm.getCandidateId(), "Interview invitation email failed: " + e.getMessage());
        }
        communications.saveAndFlush(comm);
        return CommunicationResponse.from(comm);
    }

    @Transactional
    public CallStarted logCall(Long candidateId, String actor) {
        Candidate c = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Candidate"));
        PhoneCheck phone = ContactValidator.checkPhone(c.getMobile(), settings().getDefaultCountryCode());
        if (!phone.valid()) throw ApiException.badRequest(phone.reason());
        Communication comm = new Communication();
        comm.setCandidateId(c.getId());
        comm.setCandidateName(c.getFullName());
        comm.setChannel(Channel.CALL);
        comm.setRecipient(phone.display());
        comm.setStatus(Status.CALL_INITIATED);
        comm.setInitiatedBy(actor);
        communications.save(comm);
        audit.record("CALL_INITIATED", "CANDIDATE", c.getId(), "Phone call started");
        return new CallStarted(CommunicationResponse.from(comm), "tel:+" + phone.international());
    }

    @Transactional
    public CommunicationResponse recordCallOutcome(Long id, Communication.CallOutcome outcome, String notes) {
        Communication comm = communications.findById(id).orElseThrow(() -> ApiException.notFound("Communication"));
        if (comm.getChannel() != Channel.CALL) throw ApiException.badRequest("Only calls have an outcome");
        comm.setCallOutcome(outcome);
        comm.setNotes(trimToNull(notes));
        audit.record("CALL_OUTCOME", "CANDIDATE", comm.getCandidateId(), "Call outcome: " + outcome.name().replace('_', ' ').toLowerCase()
                + (StringUtils.hasText(notes) ? " - " + notes.trim() : ""));
        return CommunicationResponse.from(comm);
    }

    /** HR confirms they pressed send in WhatsApp. This is HR's confirmation, not a delivery receipt. */
    @Transactional
    public CommunicationResponse markWhatsAppSent(Long id, String actor) {
        Communication comm = communications.findById(id).orElseThrow(() -> ApiException.notFound("Communication"));
        if (comm.getChannel() != Channel.WHATSAPP || comm.getStatus() != Status.PREPARED) {
            throw ApiException.badRequest("Only prepared WhatsApp messages can be marked as sent");
        }
        comm.setStatus(Status.MARKED_SENT);
        comm.setNotes("Marked as sent by " + actor + " (manual confirmation - delivery is not verified)");
        audit.record("WHATSAPP_MARKED_SENT", "CANDIDATE", comm.getCandidateId(), "WhatsApp invitation marked as sent by HR");
        return CommunicationResponse.from(comm);
    }

    @Transactional(readOnly = true)
    public List<CommunicationResponse> historyFor(Long candidateId) {
        if (!candidates.existsById(candidateId)) throw ApiException.notFound("Candidate");
        return communications.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream().map(CommunicationResponse::from).toList();
    }

    public TestEmailResult sendTestEmail(String to, String actor) {
        if (!email.isConfigured()) return new TestEmailResult(false, null, "Email service is not configured on the server");
        try {
            String id = email.send(to, "JPM HR Portal – test email", "This is a test email from JPM HR Portal.\n\nIf you received it, outgoing email is configured correctly.\n\n" + settings().getHrSignature());
            audit.record("EMAIL_TEST", "SETTINGS", null, "Test email accepted by " + email.providerName());
            return new TestEmailResult(true, id, null);
        } catch (EmailSender.EmailException e) {
            audit.record("EMAIL_TEST", "SETTINGS", null, "Test email failed: " + e.getMessage());
            return new TestEmailResult(false, null, e.getMessage());
        }
    }

    // ================================================================ helpers

    private Communication base(Context ctx, Channel channel, String recipient, String actor) {
        Communication comm = new Communication();
        comm.setCandidateId(ctx.candidate().getId());
        comm.setInterviewId(ctx.interview() == null ? null : ctx.interview().getId());
        comm.setChannel(channel);
        comm.setRecipient(recipient);
        comm.setCandidateName(ctx.candidate().getFullName());
        comm.setInitiatedBy(actor);
        return comm;
    }

    private static String friendly(List<String> missing) {
        return String.join(", ", missing.stream().map(m -> FRIENDLY.getOrDefault(m, m)).toList());
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
