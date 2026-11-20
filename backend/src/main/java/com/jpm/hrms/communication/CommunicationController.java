package com.jpm.hrms.communication;

import com.jpm.hrms.communication.CommunicationDtos.CallOutcomeRequest;
import com.jpm.hrms.communication.CommunicationDtos.CallStarted;
import com.jpm.hrms.communication.CommunicationDtos.CommunicationResponse;
import com.jpm.hrms.communication.CommunicationDtos.ComposeRequest;
import com.jpm.hrms.communication.CommunicationDtos.ComposeResponse;
import com.jpm.hrms.communication.CommunicationDtos.PreviewRequest;
import com.jpm.hrms.communication.CommunicationDtos.PreviewResponse;
import com.jpm.hrms.communication.CommunicationDtos.SettingsRequest;
import com.jpm.hrms.communication.CommunicationDtos.SettingsResponse;
import com.jpm.hrms.communication.CommunicationDtos.TemplateRequest;
import com.jpm.hrms.communication.CommunicationDtos.TemplateResponse;
import com.jpm.hrms.communication.CommunicationDtos.TemplatesOverview;
import com.jpm.hrms.communication.CommunicationDtos.TestEmailRequest;
import com.jpm.hrms.communication.CommunicationDtos.TestEmailResult;
import com.jpm.hrms.communication.CommunicationDtos.WhatsAppPrepared;
import com.jpm.hrms.communication.MessageTemplate.TemplateChannel;
import jakarta.validation.Valid;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class CommunicationController {
    private final CommunicationService service;

    public CommunicationController(CommunicationService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- per candidate (HR + admin)

    /** Contact validation plus rendered WhatsApp and email previews for the candidate's interview. */
    @GetMapping("/candidates/{id}/compose")
    public ComposeResponse compose(@PathVariable Long id, @RequestParam(required = false) Long interviewId,
                                   @RequestParam(defaultValue = "INTERVIEW_INVITATION") TemplatePurpose purpose) {
        return service.compose(id, interviewId, purpose);
    }

    @PostMapping("/candidates/{id}/communications/whatsapp")
    public WhatsAppPrepared whatsapp(@PathVariable Long id, @RequestBody(required = false) ComposeRequest req, Authentication auth) {
        return service.prepareWhatsApp(id, req == null ? null : req.interviewId(),
                req == null ? TemplatePurpose.INTERVIEW_INVITATION : req.purposeOrDefault(), auth.getName());
    }

    @PostMapping("/candidates/{id}/communications/email")
    public CommunicationResponse email(@PathVariable Long id, @RequestBody(required = false) ComposeRequest req, Authentication auth) {
        return service.sendEmail(id, req == null ? null : req.interviewId(),
                req == null ? TemplatePurpose.INTERVIEW_INVITATION : req.purposeOrDefault(), auth.getName());
    }

    @PostMapping("/candidates/{id}/communications/call")
    public CallStarted call(@PathVariable Long id, Authentication auth) {
        return service.logCall(id, auth.getName());
    }

    @GetMapping("/candidates/{id}/communications")
    public List<CommunicationResponse> history(@PathVariable Long id) {
        return service.historyFor(id);
    }

    @PatchMapping("/communications/{id}/call-outcome")
    public CommunicationResponse callOutcome(@PathVariable Long id, @Valid @RequestBody CallOutcomeRequest req) {
        return service.recordCallOutcome(id, req.outcome(), req.notes());
    }

    @PatchMapping("/communications/{id}/mark-sent")
    public CommunicationResponse markSent(@PathVariable Long id, Authentication auth) {
        return service.markWhatsAppSent(id, auth.getName());
    }

    @PostMapping("/communications/{id}/retry")
    public CommunicationResponse retry(@PathVariable Long id, Authentication auth) {
        return service.retry(id, auth.getName());
    }

    // ---------------------------------------------------------------- settings & templates

    /** Read access for HR too: the scheduling form shows the fixed venue. */
    @GetMapping("/communication/settings")
    public SettingsResponse settings() {
        return service.getSettings();
    }

    @PutMapping("/communication/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponse updateSettings(@Valid @RequestBody SettingsRequest req, Authentication auth) {
        return service.updateSettings(req, auth.getName());
    }

    @GetMapping("/communication/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public TemplatesOverview templates() {
        return service.overview();
    }

    @GetMapping("/communication/templates/history")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TemplateResponse> templateHistory(@RequestParam TemplateChannel channel,
                                                  @RequestParam(defaultValue = "INTERVIEW_INVITATION") TemplatePurpose purpose) {
        return service.history(purpose, channel);
    }

    @PostMapping("/communication/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public TemplateResponse saveTemplate(@Valid @RequestBody TemplateRequest req, Authentication auth) {
        return service.saveTemplate(req, auth.getName());
    }

    @PostMapping("/communication/templates/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public PreviewResponse preview(@Valid @RequestBody PreviewRequest req) {
        return service.previewDraft(req.purposeOrDefault(), req.channel(), req.subject(), req.body());
    }

    @PostMapping("/communication/test-email")
    @PreAuthorize("hasRole('ADMIN')")
    public TestEmailResult testEmail(@Valid @RequestBody TestEmailRequest req, Authentication auth) {
        return service.sendTestEmail(req.to().trim(), auth.getName());
    }

    /** Seeds the default settings and template version 1 on startup. */
    @Component
    @Order(0)
    static class DefaultsInitializer implements ApplicationRunner {
        private final CommunicationService service;

        DefaultsInitializer(CommunicationService service) {
            this.service = service;
        }

        @Override
        public void run(ApplicationArguments args) {
            service.ensureDefaults();
        }
    }
}
