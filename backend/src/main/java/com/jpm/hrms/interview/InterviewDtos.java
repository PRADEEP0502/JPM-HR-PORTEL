package com.jpm.hrms.interview;

import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class InterviewDtos {
    private InterviewDtos() {
    }

    public record InterviewResponse(Long id, Long candidateId, String candidateCode, String candidateName,
                                    String departmentName, Instant scheduledAt, int durationMinutes, String roundName,
                                    String interviewer, Interview.Mode mode, String location, InterviewStatus status,
                                    String remarks, String createdBy, Instant updatedAt) {
        public static InterviewResponse from(Interview i) {
            var c = i.getCandidate();
            String dept = c.getDepartment() != null ? c.getDepartment().getName() : c.getDepartmentRaw();
            return new InterviewResponse(i.getId(), c.getId(), c.getCandidateCode(), c.getFullName(), dept,
                    i.getScheduledAt(), i.getDurationMinutes(), i.getRoundName(), i.getInterviewer(), i.getMode(),
                    i.getLocation(), i.getStatus(), i.getRemarks(), i.getCreatedBy(), i.getUpdatedAt());
        }
    }

    public record ScheduleRequest(@NotNull Long candidateId,
                                  @NotNull Instant scheduledAt,
                                  @Min(15) @Max(480) int durationMinutes,
                                  @NotBlank @Size(max = 100) String roundName,
                                  @NotBlank @Size(max = 150) String interviewer,
                                  @NotNull Interview.Mode mode,
                                  @Size(max = 300) String location,
                                  @Size(max = 2000) String remarks) {
    }

    public record UpdateDetailsRequest(@Min(15) @Max(480) int durationMinutes,
                                       @NotBlank @Size(max = 100) String roundName,
                                       @NotBlank @Size(max = 150) String interviewer,
                                       @NotNull Interview.Mode mode,
                                       @Size(max = 300) String location,
                                       @Size(max = 2000) String remarks) {
    }

    /** newScheduledAt is required when status = RESCHEDULED. */
    public record StatusRequest(@NotNull InterviewStatus status, @Size(max = 2000) String remarks, Instant newScheduledAt) {
    }
}
