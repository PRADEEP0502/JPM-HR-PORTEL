package com.jpm.hrms.interview;

import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.department.DepartmentDirectory;
import com.jpm.hrms.interview.InterviewDtos.InterviewResponse;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds interview responses with their candidate and department, loading all candidates of a list in one query. */
@Component
public class InterviewViews {
    private final CandidateRepository candidates;
    private final DepartmentDirectory depts;

    public InterviewViews(CandidateRepository candidates, DepartmentDirectory depts) {
        this.candidates = candidates;
        this.depts = depts;
    }

    public Map<Long, Candidate> candidatesOf(Collection<Interview> interviews) {
        Map<Long, Candidate> byId = new HashMap<>();
        candidates.findAllById(interviews.stream().map(Interview::getCandidateId).distinct().toList())
                .forEach(c -> byId.put(c.getId(), c));
        return byId;
    }

    public List<InterviewResponse> toResponses(List<Interview> interviews) {
        if (interviews.isEmpty()) return List.of();
        Map<Long, Candidate> byId = candidatesOf(interviews);
        DepartmentDirectory.Names names = depts.snapshot();
        return interviews.stream().map(i -> {
            Candidate c = byId.get(i.getCandidateId());
            return InterviewResponse.from(i, c, c == null ? null : names.nameOrRaw(c));
        }).toList();
    }

    public InterviewResponse toResponse(Interview i) {
        return toResponses(List.of(i)).get(0);
    }
}
