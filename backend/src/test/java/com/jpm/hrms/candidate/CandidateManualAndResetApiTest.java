package com.jpm.hrms.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.sync.IgnoredRowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adding a candidate by hand, and the admin-only "Delete all candidates" reset. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // the reset empties the shared test database
class CandidateManualAndResetApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    IgnoredRowRepository ignoredRows;

    private static final String MANUAL = "{\"fullName\":\"Walk In Applicant\",\"mobile\":\"91234 00001\",\"email\":\"walk.in@example.com\","
            + "\"qualification\":\"ITI Fitter\",\"experienceYears\":2,\"hiringSource\":\"Street Boards\",\"version\":0}";

    @Test
    void manualCandidateThenAdminResetsEverything() throws Exception {
        String admin = login("admin", "Admin@12345");

        // manual add: MANUAL source, today's date, a real candidate ID, and NEW status
        JsonNode created = json(call(post("/api/candidates"), admin, MANUAL).andExpect(status().isCreated()));
        assertThat(created.get("fullName").asText()).isEqualTo("Walk In Applicant");
        assertThat(created.get("candidateCode").asText()).endsWith("-" + String.format("%02d", LocalDate.now().getYear() % 100));
        assertThat(created.get("applicationStatus").asText()).isEqualTo("NEW");
        Candidate saved = candidates.findById(created.get("id").asLong()).orElseThrow();
        assertThat(saved.getSource()).isEqualTo(CandidateSource.MANUAL);
        assertThat(saved.getMobile()).isEqualTo("9123400001");
        assertThat(saved.getHiringSource()).isEqualTo("Street Boards");

        // same mobile again is refused; mobile is required
        call(post("/api/candidates"), admin, MANUAL).andExpect(status().isConflict());
        call(post("/api/candidates"), admin, "{\"fullName\":\"No Phone\",\"version\":0}").andExpect(status().isBadRequest());

        call(post("/api/candidates/" + saved.getId() + "/remarks"), admin, "{\"remark\":\"Keep this remark\"}").andExpect(status().is2xxSuccessful());

        // a Google Form candidate, so the reset must remember its sheet row
        Candidate fromSheet = new Candidate();
        fromSheet.setFullName("Form Applicant");
        fromSheet.setMobile("9123400002");
        fromSheet.setSource(CandidateSource.GOOGLE_SHEETS);
        fromSheet.setSourceRowKey("reset-test-row");
        fromSheet.setApplicationDate(LocalDate.now());
        candidates.save(fromSheet);

        // HR cannot reset, and the admin must type the confirmation
        call(post("/api/users"), admin, "{\"username\":\"hr.reset\",\"fullName\":\"HR Reset\",\"role\":\"HR\",\"password\":\"HrReset@12345\"}")
                .andExpect(status().is2xxSuccessful());
        String hr = login("hr.reset", "HrReset@12345");
        call(post("/api/admin/candidates/reset"), hr, "{\"confirm\":\"DELETE ALL\"}").andExpect(status().isForbidden());
        call(post("/api/admin/candidates/reset"), admin, "{\"confirm\":\"yes\"}").andExpect(status().isBadRequest());
        assertThat(candidates.count()).isGreaterThanOrEqualTo(2);

        JsonNode reset = json(call(post("/api/admin/candidates/reset"), admin, "{\"confirm\":\"DELETE ALL\"}").andExpect(status().isOk()));
        assertThat(reset.get("removed").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(candidates.count()).isZero();
        assertThat(ignoredRows.existsById("reset-test-row")).isTrue();

        // the deletion went to the recycle bin and can be undone, remarks included
        JsonNode bin = json(mvc.perform(get("/api/admin/recycle-bin").header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        assertThat(bin).hasSize(1);
        assertThat(bin.get(0).get("names").toString()).contains("Walk In Applicant", "Form Applicant");
        mvc.perform(get("/api/admin/recycle-bin").header("Authorization", "Bearer " + hr)).andExpect(status().isForbidden());
        JsonNode restored = json(call(post("/api/admin/recycle-bin/" + bin.get(0).get("id").asText() + "/restore"), admin, "")
                .andExpect(status().isOk()));
        assertThat(restored.get("restored").asInt()).isEqualTo(reset.get("removed").asInt());
        assertThat(restored.get("skipped").asInt()).isZero();
        assertThat(candidates.findById(saved.getId())).isPresent();
        assertThat(ignoredRows.existsById("reset-test-row")).isFalse();
        JsonNode profile = json(mvc.perform(get("/api/candidates/" + saved.getId()).header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        assertThat(profile.get("remarks").toString()).contains("Keep this remark");
        mvc.perform(get("/api/admin/recycle-bin").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.length()").value(0));

        // a backup can be taken, listed and downloaded, and holds the candidates
        JsonNode backup = json(call(post("/api/admin/backups"), admin, "").andExpect(status().isOk()));
        JsonNode list = json(mvc.perform(get("/api/admin/backups").header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        assertThat(list.get(0).get("id").asText()).isEqualTo(backup.get("id").asText());
        byte[] gz = mvc.perform(get("/api/admin/backups/" + backup.get("id").asText() + "/download").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String content = new String(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz)).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("\"c\": \"candidates\"", "Walk In Applicant", "Keep this remark");
        mvc.perform(post("/api/admin/backups").header("Authorization", "Bearer " + hr)).andExpect(status().isForbidden());

        // the candidate list can be narrowed to given candidates (used by the activity report)
        JsonNode only = json(mvc.perform(get("/api/candidates").param("ids", String.valueOf(saved.getId()))
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        assertThat(only.get("totalElements").asInt()).isEqualTo(1);
        assertThat(only.get("content").get(0).get("fullName").asText()).isEqualTo("Walk In Applicant");
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token, String body)
            throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String login(String username, String password) throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }
}
