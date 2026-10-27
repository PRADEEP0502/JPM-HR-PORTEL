package com.jpm.hrms.interview;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.common.MongoText;
import com.jpm.hrms.common.PageResponse;
import com.jpm.hrms.interview.InterviewDtos.InterviewResponse;
import com.jpm.hrms.interview.InterviewDtos.ScheduleRequest;
import com.jpm.hrms.interview.InterviewDtos.StatusRequest;
import com.jpm.hrms.interview.InterviewDtos.UpdateDetailsRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class InterviewService {
    public static final List<InterviewStatus> ACTIVE = List.of(InterviewStatus.SCHEDULED, InterviewStatus.RESCHEDULED);
    private static final Duration MAX_AHEAD = Duration.ofDays(365);
    private static final int MAX_DURATION_MINUTES = 480;

    public enum Scope { UPCOMING, COMPLETED, CANCELLED, ALL }

    private final InterviewRepository interviews;
    private final CandidateRepository candidates;
    private final InterviewViews views;
    private final InterviewerService interviewers;
    private final AuditService audit;
    private final Clock clock;
    private final ZoneId zone;
    private final DateTimeFormatter displayFormat;

    public InterviewService(InterviewRepository interviews, CandidateRepository candidates, InterviewViews views,
                            InterviewerService interviewers, AuditService audit, Clock clock, ZoneId businessZone) {
        this.interviewers = interviewers;
        this.interviews = interviews;
        this.candidates = candidates;
        this.views = views;
        this.audit = audit;
        this.clock = clock;
        this.zone = businessZone;
        this.displayFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(businessZone);
    }

    public PageResponse<InterviewResponse> list(Scope scope, String q, LocalDate from, LocalDate to, int page, int size) {
        List<Criteria> cs = new ArrayList<>();
        switch (scope) {
            case UPCOMING -> cs.add(Criteria.where("status").in(ACTIVE));
            case COMPLETED -> cs.add(Criteria.where("status").is(InterviewStatus.COMPLETED));
            case CANCELLED -> cs.add(Criteria.where("status").is(InterviewStatus.CANCELLED));
            case ALL -> { }
        }
        if (from != null) cs.add(Criteria.where("scheduledAt").gte(from.atStartOfDay(zone).toInstant()));
        if (to != null) cs.add(Criteria.where("scheduledAt").lt(to.plusDays(1).atStartOfDay(zone).toInstant()));
        if (StringUtils.hasText(q)) {
            // Interviews store only the candidate id, so match candidates by name / code first.
            String text = MongoText.contains(q);
            List<Long> matching = candidates.findAll(new Criteria().orOperator(
                            Criteria.where("fullName").regex(text, "i"), Criteria.where("candidateCode").regex(text, "i")),
                    Sort.unsorted()).stream().map(Candidate::getId).toList();
            cs.add(new Criteria().orOperator(Criteria.where("candidateId").in(matching),
                    Criteria.where("interviewer").regex(text, "i")));
        }
        Criteria criteria = cs.isEmpty() ? new Criteria() : new Criteria().andOperator(cs);
        Sort sort = scope == Scope.UPCOMING ? Sort.by("scheduledAt").ascending() : Sort.by("scheduledAt").descending();
        var pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        var result = interviews.findAll(criteria, pr);
        return new PageResponse<>(views.toResponses(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public InterviewResponse schedule(ScheduleRequest req, String actor) {
        Candidate c = candidates.findById(req.candidateId()).orElseThrow(() -> ApiException.notFound("Candidate"));
        if (c.getApplicationStatus() == ApplicationStatus.REJECTED) {
            throw ApiException.badRequest("Cannot schedule an interview for a rejected candidate");
        }
        validateFutureTime(req.scheduledAt());
        checkClash(req.interviewer().trim(), req.scheduledAt(), req.durationMinutes(), null);

        Interview i = new Interview();
        i.setCandidate(c);
        i.setScheduledAt(req.scheduledAt());
        i.setDurationMinutes(req.durationMinutes());
        i.setRoundName(req.roundName().trim());
        i.setInterviewer(req.interviewer().trim());
        i.setMode(req.mode());
        i.setLocation(trimToNull(req.location()));
        i.setRemarks(trimToNull(req.remarks()));
        i.setStatus(InterviewStatus.SCHEDULED);
        i.setInterviewerUsername(interviewers.resolveInterviewerUsername(req.interviewer()));
        i.setCreatedBy(actor);
        interviews.save(i);
        refreshCandidateInterviewStatus(c);
        audit.record("INTERVIEW_SCHEDULED", "CANDIDATE", c.getId(),
                i.getRoundName() + " with " + i.getInterviewer() + " on " + displayFormat.format(i.getScheduledAt()));
        return views.toResponse(i);
    }

    public InterviewResponse updateDetails(Long id, UpdateDetailsRequest req) {
        Interview i = interviews.findById(id).orElseThrow(() -> ApiException.notFound("Interview"));
        if (!i.getStatus().isActive()) throw ApiException.badRequest("Only scheduled interviews can be edited");
        if (!i.getInterviewer().equalsIgnoreCase(req.interviewer().trim()) || i.getDurationMinutes() != req.durationMinutes()) {
            checkClash(req.interviewer().trim(), i.getScheduledAt(), req.durationMinutes(), i.getId());
        }
        i.setDurationMinutes(req.durationMinutes());
        i.setRoundName(req.roundName().trim());
        i.setInterviewer(req.interviewer().trim());
        i.setMode(req.mode());
        i.setLocation(trimToNull(req.location()));
        i.setRemarks(trimToNull(req.remarks()));
        i.setInterviewerUsername(interviewers.resolveInterviewerUsername(req.interviewer()));
        interviews.save(i);
        audit.record("INTERVIEW_UPDATED", "CANDIDATE", i.getCandidateId(),
                "Updated " + i.getRoundName() + " details (interviewer " + i.getInterviewer() + ")");
        return views.toResponse(i);
    }

    public InterviewResponse updateStatus(Long id, StatusRequest req) {
        Interview i = interviews.findById(id).orElseThrow(() -> ApiException.notFound("Interview"));
        InterviewStatus from = i.getStatus();
        if (!from.isActive()) {
            throw ApiException.badRequest("This interview is already " + from.label.toLowerCase() + " and cannot be changed");
        }
        Instant now = clock.instant();
        String detail;
        switch (req.status()) {
            case COMPLETED -> {
                if (i.getScheduledAt().isAfter(now)) {
                    throw ApiException.badRequest("An interview cannot be marked completed before its scheduled time");
                }
                detail = i.getRoundName() + " marked completed";
            }
            case CANCELLED -> detail = i.getRoundName() + " cancelled";
            case RESCHEDULED -> {
                if (req.newScheduledAt() == null) throw ApiException.badRequest("A new date and time is required to reschedule");
                if (req.newScheduledAt().equals(i.getScheduledAt())) throw ApiException.badRequest("Choose a different date or time");
                validateFutureTime(req.newScheduledAt());
                checkClash(i.getInterviewer(), req.newScheduledAt(), i.getDurationMinutes(), i.getId());
                detail = i.getRoundName() + " rescheduled from " + displayFormat.format(i.getScheduledAt())
                        + " to " + displayFormat.format(req.newScheduledAt());
                i.setScheduledAt(req.newScheduledAt());
            }
            default -> throw ApiException.badRequest("Status can only be changed to Completed, Cancelled or Rescheduled");
        }
        i.setStatus(req.status());
        if (StringUtils.hasText(req.remarks())) {
            i.setRemarks(req.remarks().trim());
            detail += " - " + req.remarks().trim();
        }
        interviews.save(i);
        candidates.findById(i.getCandidateId()).ifPresent(this::refreshCandidateInterviewStatus);
        audit.record("INTERVIEW_" + req.status().name(), "CANDIDATE", i.getCandidateId(), detail);
        return views.toResponse(i);
    }

    /**
     * The candidate-level interview status mirrors their most relevant interview: the next active one
     * if any, otherwise the latest interview; NOT_SCHEDULED when there are none. Saves the candidate.
     */
    public void refreshCandidateInterviewStatus(Candidate c) {
        List<Interview> all = interviews.findByCandidateIdOrderByScheduledAtDesc(c.getId());
        Optional<Interview> nextActive = all.stream().filter(x -> x.getStatus().isActive())
                .min(Comparator.comparing(Interview::getScheduledAt));
        InterviewStatus status = nextActive.map(Interview::getStatus)
                .orElse(all.isEmpty() ? InterviewStatus.NOT_SCHEDULED : all.get(0).getStatus());
        c.setInterviewStatus(status);
        candidates.save(c);
    }

    private void validateFutureTime(Instant when) {
        Instant now = clock.instant();
        if (!when.isAfter(now)) throw ApiException.badRequest("Interview date and time must be in the future");
        if (when.isAfter(now.plus(MAX_AHEAD))) throw ApiException.badRequest("Interview cannot be scheduled more than a year ahead");
    }

    private void checkClash(String interviewer, Instant start, int durationMinutes, Long excludeId) {
        Instant end = start.plus(Duration.ofMinutes(durationMinutes));
        List<Interview> nearby = interviews.findClashes(interviewer, ACTIVE,
                start.minus(Duration.ofMinutes(MAX_DURATION_MINUTES)), end, excludeId == null ? -1L : excludeId);
        var names = views.candidatesOf(nearby);
        for (Interview other : nearby) {
            Instant otherEnd = other.getScheduledAt().plus(Duration.ofMinutes(other.getDurationMinutes()));
            if (other.getScheduledAt().isBefore(end) && otherEnd.isAfter(start)) {
                throw ApiException.conflict(interviewer + " already has an interview at "
                        + displayFormat.format(other.getScheduledAt()) + " (" + (names.containsKey(other.getCandidateId()) ? names.get(other.getCandidateId()).getFullName() : "another candidate") + ")");
            }
        }
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
