package com.jpm.hrms.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.candidate.CandidateService;
import com.jpm.hrms.communication.MessageTemplate.TemplateChannel;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Templates for every purpose: defaults, rules per purpose, independent versions and per-purpose messages. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TemplatePurposeApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    CommunicationService service;
    @Autowired
    MongoOperations mongo;

    @Test
    void everyPurposeHasApprovedTemplatesWithItsOwnRules() throws Exception {
        String admin = login();
        JsonNode overview = json(get("/api/communication/templates"), admin);
        assertThat(overview.get("purposes")).hasSize(6);
        for (JsonNode p : overview.get("purposes")) {
            assertThat(p.get("whatsapp").get("body").asText()).contains("{{CandidateName}}").doesNotContain("Street");
            assertThat(p.get("email").get("subject").asText()).endsWith("– JPM");
        }

        // Interview placeholders are not allowed in a selection message; candidate name is still required
        mvc.perform(post("/api/communication/templates").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"SELECTION\",\"channel\":\"WHATSAPP\",\"body\":\"Hi {{CandidateName}}, see you on {{InterviewDate}}\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/communication/templates").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"SELECTION\",\"channel\":\"WHATSAPP\",\"body\":\"Congratulations!\"}"))
                .andExpect(status().isBadRequest());

        // A new selection version does not change the invitation's version numbering
        int invitationVersion = json(get("/api/communication/templates/history").param("channel", "WHATSAPP")
                .param("purpose", "INTERVIEW_INVITATION"), admin).get(0).get("version").asInt();
        mvc.perform(post("/api/communication/templates").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"SELECTION\",\"channel\":\"WHATSAPP\",\"body\":\"Dear {{CandidateName}}, you are selected for {{Department}}.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        assertThat(json(get("/api/communication/templates/history").param("channel", "WHATSAPP")
                .param("purpose", "INTERVIEW_INVITATION"), admin).get(0).get("version").asInt()).isEqualTo(invitationVersion);
    }

    @Test
    void rejectionMessageNeedsNoInterview() throws Exception {
        String admin = login();
        Candidate c = candidate();

        // Invitation is blocked without an interview; rejection is ready
        mvc.perform(get("/api/candidates/" + c.getId() + "/compose").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.whatsapp.ready").value(false));
        mvc.perform(get("/api/candidates/" + c.getId() + "/compose").param("purpose", "REJECTION").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.purposeLabel").value("Rejection notification"))
                .andExpect(jsonPath("$.whatsapp.ready").value(true))
                .andExpect(jsonPath("$.whatsapp.message").value(org.hamcrest.Matchers.containsString("Dear Purpose Test,")))
                .andExpect(jsonPath("$.email.subject").value("Application Update – JPM"));

        JsonNode prepared = mapper.readTree(mvc.perform(post("/api/candidates/" + c.getId() + "/communications/whatsapp")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"REJECTION\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(prepared.get("communication").get("purpose").asText()).isEqualTo("REJECTION");
        assertThat(prepared.get("communication").get("status").asText()).isEqualTo("PREPARED"); // never claimed as sent
        assertThat(prepared.get("url").asText()).startsWith("https://wa.me/91");
    }

    @Test
    void templatesSavedBeforePurposesBecomeInvitations() {
        mongo.updateFirst(new Query(Criteria.where("purpose").is("INTERVIEW_INVITATION").and("channel").is("EMAIL").and("active").is(true)),
                new Update().unset("purpose"), MessageTemplate.class);
        service.ensureDefaults();
        assertThat(service.activeTemplate(TemplatePurpose.INTERVIEW_INVITATION, TemplateChannel.EMAIL).getSubject())
                .isEqualTo("Interview Invitation – JPM");
        assertThat(mongo.count(new Query(Criteria.where("purpose").exists(false)), MessageTemplate.class)).isZero();
    }

    private Candidate candidate() {
        Department d = departments.findByNameIgnoreCase("Purpose Dept").orElseGet(() -> departments.save(new Department("Purpose Dept", null)));
        Candidate c = new Candidate();
        c.setFullName("Purpose Test");
        c.setMobile("9876501234");
        c.setEmail("purpose.test@example.com");
        c.setDepartment(d);
        c.setApplicationDate(LocalDate.now());
        c.setAppliedAt(Instant.now());
        c.setSource(CandidateSource.MANUAL);
        candidates.save(c);
        CandidateService.assignCode(c, candidates);
        return candidates.save(c);
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token) throws Exception {
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
