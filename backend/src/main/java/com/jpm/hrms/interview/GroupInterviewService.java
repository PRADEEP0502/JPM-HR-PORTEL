package com.jpm.hrms.interview;

import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.communication.Communication;
import com.jpm.hrms.communication.CommunicationService;
import com.jpm.hrms.communication.TemplatePurpose;
import com.jpm.hrms.interview.InterviewDtos.GroupScheduleRequest;
import com.jpm.hrms.interview.InterviewDtos.GroupScheduleResult;
import com.jpm.hrms.interview.InterviewDtos.InterviewResponse;
import com.jpm.hrms.interview.InterviewDtos.ScheduleRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Schedules many candidates at the same date and time, each with their own interview record and invitation. */
@Service
public class GroupInterviewService {
    private final InterviewService interviews;
    private final CandidateRepository candidates;
    private final CommunicationService communication;

    public GroupInterviewService(InterviewService interviews, CandidateRepository candidates, CommunicationService communication) {
        this.interviews = interviews;
        this.candidates = candidates;
        this.communication = communication;
    }

    /** A candidate that cannot be scheduled (rejected, already booked) is reported and the rest go ahead. */
    public List<GroupScheduleResult> schedule(GroupScheduleRequest req, String actor) {
        List<GroupScheduleResult> results = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(req.candidateIds())) {
            String name = candidates.findById(id).map(Candidate::getFullName).orElse("Candidate " + id);
            InterviewResponse interview;
            try {
                interview = interviews.schedule(new ScheduleRequest(id, req.scheduledAt(), req.durationMinutes(), req.roundName(),
                        req.interviewer(), req.mode(), req.location(), req.remarks()), actor, true);
            } catch (ApiException e) {
                results.add(new GroupScheduleResult(id, name, null, e.getMessage(), false, null));
                continue;
            }
            boolean emailSent = false;
            String emailError = null;
            if (req.sendEmail()) {
                try {
                    var sent = communication.sendEmail(id, interview.id(), TemplatePurpose.INTERVIEW_INVITATION, actor);
                    emailSent = sent.status() != Communication.Status.FAILED;
                    if (!emailSent) emailError = sent.errorMessage() != null ? sent.errorMessage() : "Email was not accepted";
                } catch (ApiException e) {
                    emailError = e.getMessage();
                }
            }
            results.add(new GroupScheduleResult(id, name, interview, null, emailSent, emailError));
        }
        return results;
    }
}
