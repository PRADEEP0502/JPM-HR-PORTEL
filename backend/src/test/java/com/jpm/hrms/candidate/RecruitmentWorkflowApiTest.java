package com.jpm.hrms.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.interview.Interview;
import com.jpm.hrms.interview.InterviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Statuses with history, archive, the Interviewer role's limited access, and CSV export. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruitmentWorkflowApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    InterviewRepository interviews;

    @Test
    void statusHistoryArchiveAndFilters() throws Exception {
        String admin = login("admin", "Admin@12345");
        Candidate c = candidate("Workflow Status Test", LocalDate.of(2025, 3, 10));

        call(patch("/api/candidates/" + c.getId() + "/status"), admin, "{\"applicationStatus\":\"INTERVIEW_SCHEDULED\",\"note\":\"Round 1 booked\"}");
        call(patch("/api/candidates/" + c.getId() + "/status"), admin, "{\"applicationStatus\":\"JOINED\"}");
        mvc.perform(get("/api/candidates/" + c.getId()).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.applicationStatus").value("JOINED"))
                .andExpect(jsonPath("$.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.statusHistory[0].fromStatus").value("INTERVIEW_SCHEDULED"))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("JOINED"))
                .andExpect(jsonPath("$.statusHistory[0].changedBy").value("admin"))
                .andExpect(jsonPath("$.statusHistory[1].note").value("Round 1 booked"));

        // Several statuses at once, and an application date range
        assertThat(names(admin, "applicationStatus", "NEW,JOINED")).contains("Workflow Status Test");
        assertThat(names(admin, "applicationStatus", "NEW")).doesNotContain("Workflow Status Test");
        assertThat(names(admin, "appliedFrom", "2025-03-01", "appliedTo", "2025-03-31")).containsExactly("Workflow Status Test");
        mvc.perform(get("/api/candidates").param("appliedFrom", "2025-04-01").param("appliedTo", "2025-03-01")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());

        // Archive hides from the active list; nothing is deleted; restore brings it back
        call(patch("/api/candidates/" + c.getId() + "/archive"), admin, "{\"archived\":true,\"reason\":\"Joined in 2025\"}");
        assertThat(names(admin)).doesNotContain("Workflow Status Test");
        assertThat(names(admin, "archive", "ARCHIVED")).containsExactly("Workflow Status Test");
        assertThat(candidates.findById(c.getId())).isPresent();
        mvc.perform(patch("/api/candidates/" + c.getId() + "/archive").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isBadRequest());
        call(patch("/api/candidates/" + c.getId() + "/archive"), admin, "{\"archived\":false}");
        assertThat(names(admin)).contains("Workflow Status Test");

        // CSV export: header row, UTF-8 with BOM, filtered rows
        String csv = new String(mvc.perform(get("/api/candidates/export").param("format", "csv").param("q", "Workflow Status")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Candidate ID,Name,");
        assertThat(csv).contains("Workflow Status Test").contains("Joined");
    }

    @Test
    void interviewerSeesOnlyAssignedInterviewsAndLimitedDetails() throws Exception {
        String admin = login("admin", "Admin@12345");
        call(post("/api/users"), admin, "{\"username\":\"iv.meena\",\"fullName\":\"Meena Interviewer\",\"role\":\"INTERVIEWER\",\"password\":\"Meena@12345\"}");
        call(post("/api/users"), admin, "{\"username\":\"iv.other\",\"fullName\":\"Other Interviewer\",\"role\":\"INTERVIEWER\",\"password\":\"Other@12345\"}");
        Candidate c = candidate("Interviewer Access Test", LocalDate.now());
        Instant at = Instant.now().plus(Duration.ofDays(3));
        JsonNode scheduled = call(post("/api/interviews"), admin, "{\"candidateId\":" + c.getId() + ",\"scheduledAt\":\"" + at
                + "\",\"durationMinutes\":45,\"roundName\":\"Technical\",\"interviewer\":\"meena interviewer\",\"mode\":\"IN_PERSON\"}");
        long interviewId = scheduled.get("id").asLong();
        assertThat(scheduled.get("interviewerUsername").asText()).isEqualTo("iv.meena");

        String meena = login("iv.meena", "Meena@12345");
        String other = login("iv.other", "Other@12345");

        // HR areas are closed to interviewers
        for (String url : List.of("/api/candidates", "/api/candidates/" + c.getId(), "/api/dashboard", "/api/interviews", "/api/reports/DEPARTMENT")) {
            mvc.perform(get(url).header("Authorization", "Bearer " + meena)).andExpect(status().isForbidden());
        }

        // Own interview: candidate summary without contact details or the HR rating
        String body = mvc.perform(get("/api/my/interviews").header("Authorization", "Bearer " + meena))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].candidate.fullName").value("Interviewer Access Test"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("mobile").doesNotContain("@example.com").doesNotContain("profileRating");

        // Someone else's interview does not exist for them
        mvc.perform(get("/api/my/interviews").header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/my/interviews/" + interviewId).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/my/interviews/" + interviewId + "/feedback").header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"recommendation\":\"RECOMMEND\",\"feedback\":\"x\"}"))
                .andExpect(status().isNotFound());
        // HR users cannot use the interviewer endpoints either
        mvc.perform(get("/api/my/interviews").header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());

        // Feedback only after the interview has started
        mvc.perform(post("/api/my/interviews/" + interviewId + "/feedback").header("Authorization", "Bearer " + meena)
                .contentType(MediaType.APPLICATION_JSON).content("{\"recommendation\":\"RECOMMEND\",\"feedback\":\"Good\"}"))
                .andExpect(status().isBadRequest());
        Interview i = interviews.findById(interviewId).orElseThrow();
        i.setScheduledAt(Instant.now().minus(Duration.ofHours(1)));
        interviews.save(i);
        call(post("/api/my/interviews/" + interviewId + "/feedback"), meena,
                "{\"recommendation\":\"STRONGLY_RECOMMEND\",\"feedback\":\"Solid Java fundamentals\"}");

        // HR sees the feedback on the candidate profile
        mvc.perform(get("/api/candidates/" + c.getId()).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.interviews[0].recommendation").value("STRONGLY_RECOMMEND"))
                .andExpect(jsonPath("$.interviews[0].feedback").value("Solid Java fundamentals"))
                .andExpect(jsonPath("$.interviews[0].feedbackBy").value("iv.meena"));
    }

    @Test
    void everyReportBuildsAndExportsAndPipelineHasAllStatuses() throws Exception {
        String admin = login("admin", "Admin@12345");
        Candidate c = candidate("Report Pipeline Test", LocalDate.now());
        call(patch("/api/candidates/" + c.getId() + "/rating"), admin, "{\"rating\":8}");

        for (var type : com.jpm.hrms.report.ReportService.ReportType.values()) {
            mvc.perform(get("/api/reports/" + type).header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
            mvc.perform(get("/api/reports/" + type + "/export").param("format", "csv").header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/reports/RATING_DISTRIBUTION").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.rows[7].rating").value("8/10"))
                .andExpect(jsonPath("$.rows[7].band").value("Good Match"))
                .andExpect(jsonPath("$.rows[10].rating").value("Not Rated"));
        mvc.perform(get("/api/reports/ACTIVITY").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.rows[0].fullName").value("Report Pipeline Test"))
                .andExpect(jsonPath("$.rows[0].action").value("rating updated"));
        mvc.perform(get("/api/reports/APPLICATIONS_BY_DATE").param("from", "2020-01-01").param("to", "2026-12-31")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());

        JsonNode pipeline = mapper.readTree(mvc.perform(get("/api/pipeline").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(pipeline).hasSize(CandidateEnums.ApplicationStatus.values().length);
        assertThat(pipeline.get(0).get("status").asText()).isEqualTo("NEW");
        assertThat(pipeline.get(0).toString()).contains("Report Pipeline Test");
    }

    private Candidate candidate(String name, LocalDate applied) {
        Candidate c = new Candidate();
        c.setFullName(name);
        c.setEmail(name.replace(' ', '.').toLowerCase() + "@example.com");
        c.setMobile("9876543210");
        c.setApplicationDate(applied);
        c.setAppliedAt(applied.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        c.setSource(CandidateSource.MANUAL);
        candidates.save(c);
        CandidateService.assignCode(c, candidates);
        return candidates.save(c);
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String token, String json) throws Exception {
        String res = mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return res.isEmpty() ? null : mapper.readTree(res);
    }

    private List<String> names(String token, String... params) throws Exception {
        var req = get("/api/candidates").param("size", "100").header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        String body = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> out = new ArrayList<>();
        for (JsonNode n : mapper.readTree(body).get("content")) out.add(n.get("fullName").asText());
        return out;
    }

    private String login(String username, String password) throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }
}
