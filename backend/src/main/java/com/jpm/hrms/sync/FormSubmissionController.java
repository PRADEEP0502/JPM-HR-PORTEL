package com.jpm.hrms.sync;

import com.jpm.hrms.sync.SyncService.FormSubmission;
import com.jpm.hrms.sync.SyncService.FormSubmissionResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Google Form responses from the form's Apps Script trigger. No sign-in: the request carries the form key
 * an admin copies from Settings, and a wrong key is refused.
 */
@RestController
public class FormSubmissionController {
    private final SyncService service;

    public FormSubmissionController(SyncService service) {
        this.service = service;
    }

    @PostMapping("/api/public/form-submissions")
    public FormSubmissionResult submit(@Valid @RequestBody FormSubmission submission) {
        return service.importSubmission(submission);
    }
}
