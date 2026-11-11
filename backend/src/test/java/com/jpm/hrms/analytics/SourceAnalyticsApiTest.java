package com.jpm.hrms.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.candidate.CandidateService;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import com.jpm.hrms.requirement.HiringRequirement;
import com.jpm.hrms.requirement.HiringRequirementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourceAnalyticsApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    HiringRequirementRepository requirements;

    // a date range no other test uses, so the counts are exact
    private static final LocalDate DAY = LocalDate.of(2019, 3, 5);

    @Test
    void groupsCandidatesBySourceWithOutcomesTrendsAndRequirementHires() throws Exception {
        String admin = login();
        Department dept = departments.save(new Department("Source Analytics Dept", null));
        requirements.save(new HiringRequirement(dept.getId(), "Helper", 1, "admin"));

        candidate("SA Indeed One", "Indeed", ApplicationStatus.SELECTED, dept, "Helper", DAY);
        candidate("SA Indeed Two", " indeed ", ApplicationStatus.REJECTED, dept, null, DAY);
        candidate("SA Indeed Three", "Indeed", ApplicationStatus.NEW, null, null, DAY.plusDays(1));
        candidate("SA Street", "Street Boards", ApplicationStatus.JOINED, dept, "Helper", DAY.plusDays(1));
        candidate("SA Blank", null, ApplicationStatus.UNDER_REVIEW, null, null, DAY.plusDays(2));
        candidate("SA Typed", "Friend told me", ApplicationStatus.NEW, null, null, DAY.plusDays(2));

        JsonNode a = json(get("/api/analytics/sources").param("from", DAY.toString()).param("to", DAY.plusDays(2).toString()), admin);
        assertThat(a.get("totalApplications").asInt()).isEqualTo(6);
        assertThat(a.get("granularity").asText()).isEqualTo("DAY");
        assertThat(a.get("topSource").asText()).isEqualTo("Indeed");
        JsonNode rows = a.get("sources");
        assertThat(rows).hasSize(8);
        assertThat(rows.get(7).get("source").asText()).isEqualTo("Not specified");
        JsonNode indeed = rows.get(0);
        assertThat(indeed.get("applications").asInt()).isEqualTo(3);
        assertThat(indeed.get("selected").asInt()).isEqualTo(1);
        assertThat(indeed.get("rejected").asInt()).isEqualTo(1);
        assertThat(indeed.get("pending").asInt()).isEqualTo(1);
        assertThat(indeed.get("selectionRate").asDouble()).isEqualTo(33.3);
        assertThat(indeed.get("share").asDouble()).isEqualTo(50.0);
        assertThat(rows.get(2).get("joined").asInt()).isEqualTo(1); // Street Boards
        assertThat(rows.get(6).get("applications").asInt()).isEqualTo(1); // typed-in answer counts as Others
        assertThat(rows.get(7).get("applications").asInt()).isEqualTo(1); // blank is Not specified

        // requirement of 1 Helper: the earlier applicant (Indeed, 5 Mar) fills it, not the later one
        assertThat(a.get("topRequirementSource").asText()).isEqualTo("Indeed");
        JsonNode req = a.get("requirements").get(0);
        assertThat(req.get("filled").asInt()).isEqualTo(1);
        assertThat(req.get("filledBySource").get("Indeed").asInt()).isEqualTo(1);

        JsonNode trend = a.get("trend");
        assertThat(trend).hasSize(3);
        assertThat(trend.get(0).get("applications").get("Indeed").asInt()).isEqualTo(2);
        assertThat(trend.get(2).get("applications").get("Not specified").asInt()).isEqualTo(1);

        // filters: source and department
        JsonNode onlyIndeed = json(get("/api/analytics/sources").param("from", DAY.toString()).param("to", DAY.plusDays(2).toString())
                .param("source", "Indeed"), admin);
        assertThat(onlyIndeed.get("totalApplications").asInt()).isEqualTo(3);
        assertThat(onlyIndeed.get("sources")).hasSize(1);
        JsonNode inDept = json(get("/api/analytics/sources").param("from", DAY.toString()).param("to", DAY.plusDays(2).toString())
                .param("departmentId", String.valueOf(dept.getId())), admin);
        assertThat(inDept.get("totalApplications").asInt()).isEqualTo(3);
        JsonNode monthly = json(get("/api/analytics/sources").param("from", DAY.toString()).param("to", DAY.plusDays(2).toString())
                .param("granularity", "MONTH"), admin);
        assertThat(monthly.get("trend")).hasSize(1);
        assertThat(monthly.get("trend").get(0).get("label").asText()).isEqualTo("Mar 2019");

        // the candidate list opens the same groups
        assertThat(names(admin, "Not specified")).containsExactly("SA Blank");
        assertThat(names(admin, "Others")).containsExactly("SA Typed");
        assertThat(names(admin, "Indeed")).containsExactlyInAnyOrder("SA Indeed One", "SA Indeed Two", "SA Indeed Three");

        mvc.perform(get("/api/analytics/sources/export").param("format", "csv").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/analytics/sources").param("source", "Facebook").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categorisesTypedSources() {
        assertThat(SourceAnalyticsService.category(null)).isEqualTo("Not specified");
        assertThat(SourceAnalyticsService.category("  ")).isEqualTo("Not specified");
        assertThat(SourceAnalyticsService.category("SOCIAL MEDIA")).isEqualTo("Social Media");
        assertThat(SourceAnalyticsService.category("Manpower agency")).isEqualTo("Man Power Agencies");
        assertThat(SourceAnalyticsService.category("Other")).isEqualTo("Others");
        assertThat(SourceAnalyticsService.category("Walk-in")).isEqualTo("Others");
    }

    private void candidate(String name, String source, ApplicationStatus status, Department dept, String position, LocalDate applied) {
        Candidate c = new Candidate();
        c.setFullName(name);
        c.setMobile(String.valueOf(9000000000L + Math.abs(name.hashCode() % 99999999)));
        c.setHiringSource(source);
        c.setApplicationStatus(status);
        c.setDepartmentId(dept == null ? null : dept.getId());
        c.setPosition(position);
        c.setApplicationDate(applied);
        c.setAppliedAt(applied.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        c.setSource(CandidateSource.MANUAL);
        candidates.save(c);
        CandidateService.assignCode(c, candidates);
        candidates.save(c);
    }

    private List<String> names(String token, String source) throws Exception {
        JsonNode page = json(get("/api/candidates").param("hiringSource", source).param("appliedFrom", DAY.toString())
                .param("appliedTo", DAY.plusDays(2).toString()).param("size", "50"), token);
        List<String> out = new ArrayList<>();
        page.get("content").forEach(n -> out.add(n.get("fullName").asText()));
        return out;
    }

    private JsonNode json(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mapper.readTree(mvc.perform(req.header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String login() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }
}
