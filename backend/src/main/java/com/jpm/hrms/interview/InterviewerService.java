package com.jpm.hrms.interview;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.department.DepartmentDirectory;
import com.jpm.hrms.document.CandidateDocument.DocType;
import com.jpm.hrms.document.CandidateDocumentRepository;
import com.jpm.hrms.document.DocumentService;
import com.jpm.hrms.interview.InterviewDtos.InterviewResponse;
import com.jpm.hrms.user.AppUser;
import com.jpm.hrms.user.AppUserRepository;
import com.jpm.hrms.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What an Interviewer may do: see only the interviews assigned to their account, a limited candidate summary
 * (no contact details, date of birth, address, salary, references, HR remarks or Profile Match Rating), the
 * candidate's resume, and record feedback. Interviews of other people return 404, not 403, so their existence
 * is not revealed.
 */
@Service
public class InterviewerService {

    public record CandidateSummary(Long id, String candidateCode, String fullName, String departmentName,
                                   String qualification, String institution, Integer passingYear, BigDecimal experienceYears,
                                   String previousCompany, String previousDesignation, String skills, boolean hasResume) {
    }

    public record AssignedInterview(InterviewResponse interview, CandidateSummary candidate) {
    }

    public record FeedbackRequest(@NotNull Interview.Recommendation recommendation,
                                  @NotBlank @Size(max = 4000) String feedback) {
    }

    public record InterviewerOption(String username, String fullName) {
    }

    private final InterviewRepository interviews;
    private final CandidateRepository candidates;
    private final CandidateDocumentRepository documents;
    private final DocumentService documentService;
    private final AppUserRepository users;
    private final InterviewViews views;
    private final DepartmentDirectory depts;
    private final AuditService audit;
    private final Clock clock;

    public InterviewerService(InterviewRepository interviews, CandidateRepository candidates,
                              CandidateDocumentRepository documents, DocumentService documentService, AppUserRepository users,
                              InterviewViews views, DepartmentDirectory depts, AuditService audit, Clock clock) {
        this.interviews = interviews;
        this.candidates = candidates;
        this.documents = documents;
        this.documentService = documentService;
        this.users = users;
        this.views = views;
        this.depts = depts;
        this.audit = audit;
        this.clock = clock;
    }

    /** Active Interviewer accounts, for HR picking an interviewer when scheduling. */
    public List<InterviewerOption> interviewerAccounts() {
        return users.findByRoleAndEnabledTrueOrderByFullNameAsc(Role.INTERVIEWER).stream()
                .map(u -> new InterviewerOption(u.getUsername(), u.getFullName())).toList();
    }

    /**
     * Links an interview to an Interviewer account when the interviewer name matches the account's full name or
     * username (case-insensitive). Returns null when there is no such account.
     */
    public String resolveInterviewerUsername(String interviewerName) {
        return users.findByRoleAndEnabledTrueOrderByFullNameAsc(Role.INTERVIEWER).stream()
                .filter(u -> u.getFullName().equalsIgnoreCase(interviewerName.trim()) || u.getUsername().equalsIgnoreCase(interviewerName.trim()))
                .map(AppUser::getUsername).findFirst().orElse(null);
    }

    public List<AssignedInterview> myInterviews(String username) {
        List<Interview> mine = interviews.findByInterviewerUsernameOrderByScheduledAtDesc(username);
        var responses = views.toResponses(mine);
        var byId = views.candidatesOf(mine);
        var names = depts.snapshot();
        return java.util.stream.IntStream.range(0, mine.size()).mapToObj(i -> {
            Candidate c = byId.get(mine.get(i).getCandidateId());
            return new AssignedInterview(responses.get(i), c == null ? null : summary(c, names));
        }).toList();
    }

    public AssignedInterview myInterview(Long id, String username) {
        Interview i = assigned(id, username);
        Candidate c = candidates.findById(i.getCandidateId()).orElse(null);
        return new AssignedInterview(views.toResponse(i), c == null ? null : summary(c, depts.snapshot()));
    }

    public ResponseEntity<InputStreamResource> resume(Long id, String username) {
        Interview i = assigned(id, username);
        return documentService.downloadLatestResume(i.getCandidateId());
    }

    public AssignedInterview recordFeedback(Long id, FeedbackRequest req, String username) {
        Interview i = assigned(id, username);
        if (i.getScheduledAt().isAfter(clock.instant())) {
            throw ApiException.badRequest("Feedback can be recorded once the interview has started");
        }
        if (i.getStatus() == com.jpm.hrms.candidate.CandidateEnums.InterviewStatus.CANCELLED) {
            throw ApiException.badRequest("This interview was cancelled");
        }
        boolean update = i.getFeedback() != null;
        i.setRecommendation(req.recommendation());
        i.setFeedback(req.feedback().trim());
        i.setFeedbackBy(username);
        i.setFeedbackAt(Instant.now());
        interviews.save(i);
        audit.record("INTERVIEW_FEEDBACK", "CANDIDATE", i.getCandidateId(), (update ? "Interview feedback updated" : "Interview feedback recorded")
                + " for " + i.getRoundName() + ": " + req.recommendation().name().replace('_', ' ').toLowerCase());
        return myInterview(id, username);
    }

    private Interview assigned(Long id, String username) {
        Optional<Interview> i = interviews.findById(id);
        if (i.isEmpty() || !username.equalsIgnoreCase(i.get().getInterviewerUsername())) throw ApiException.notFound("Interview");
        return i.get();
    }

    private CandidateSummary summary(Candidate c, DepartmentDirectory.Names names) {
        boolean resume = documents.findFirstByCandidateIdAndDocTypeOrderByCreatedAtDesc(c.getId(), DocType.RESUME).isPresent();
        return new CandidateSummary(c.getId(), c.getCandidateCode(), c.getFullName(), names.nameOrRaw(c), c.getQualification(),
                c.getInstitution(), c.getPassingYear(), c.getExperienceYears(), c.getPreviousCompany(), c.getPreviousDesignation(),
                c.getSkills(), resume);
    }
}
