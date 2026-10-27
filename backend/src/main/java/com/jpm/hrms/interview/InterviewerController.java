package com.jpm.hrms.interview;

import com.jpm.hrms.interview.InterviewerService.AssignedInterview;
import com.jpm.hrms.interview.InterviewerService.FeedbackRequest;
import com.jpm.hrms.interview.InterviewerService.InterviewerOption;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints for the Interviewer role (their own interviews only) and the interviewer list HR schedules with. */
@RestController
public class InterviewerController {
    private final InterviewerService service;

    public InterviewerController(InterviewerService service) {
        this.service = service;
    }

    @GetMapping("/api/interviewers")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public List<InterviewerOption> interviewers() {
        return service.interviewerAccounts();
    }

    @GetMapping("/api/my/interviews")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public List<AssignedInterview> mine(Authentication auth) {
        return service.myInterviews(auth.getName());
    }

    @GetMapping("/api/my/interviews/{id}")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public AssignedInterview one(@PathVariable Long id, Authentication auth) {
        return service.myInterview(id, auth.getName());
    }

    @GetMapping("/api/my/interviews/{id}/resume")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public ResponseEntity<InputStreamResource> resume(@PathVariable Long id, Authentication auth) {
        return service.resume(id, auth.getName());
    }

    @PostMapping("/api/my/interviews/{id}/feedback")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public AssignedInterview feedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest req, Authentication auth) {
        return service.recordFeedback(id, req, auth.getName());
    }
}
