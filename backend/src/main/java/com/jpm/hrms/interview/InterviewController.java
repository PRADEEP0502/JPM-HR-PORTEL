package com.jpm.hrms.interview;

import com.jpm.hrms.common.PageResponse;
import com.jpm.hrms.interview.InterviewDtos.InterviewResponse;
import com.jpm.hrms.interview.InterviewDtos.ScheduleRequest;
import com.jpm.hrms.interview.InterviewDtos.StatusRequest;
import com.jpm.hrms.interview.InterviewDtos.UpdateDetailsRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/interviews")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class InterviewController {
    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InterviewResponse> list(@RequestParam(defaultValue = "UPCOMING") InterviewService.Scope scope,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return service.list(scope, q, from, to, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse schedule(@Valid @RequestBody ScheduleRequest req, Authentication auth) {
        return service.schedule(req, auth.getName());
    }

    @PutMapping("/{id}")
    public InterviewResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDetailsRequest req) {
        return service.updateDetails(id, req);
    }

    @PatchMapping("/{id}/status")
    public InterviewResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return service.updateStatus(id, req);
    }
}
