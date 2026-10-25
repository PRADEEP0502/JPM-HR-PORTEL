package com.jpm.hrms.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jpm.hrms.PostgresTestSupport;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.candidate.CandidateService;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests of the communication module against PostgreSQL and a real (in-process) SMTP server. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommunicationApiTest extends PostgresTestSupport {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(false);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String VENUE = "JPM – Junior Processing Mill";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    DepartmentRepository departments;

    private String admin;
    private String hr;

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin", "Admin@12345");
        String hrName = "hr-" + UUID.randomUUID().toString().substring(0, 8);
        call(post("/api/users"), admin, "{\"username\":\"" + hrName + "\",\"fullName\":\"HR Tester\",\"role\":\"HR\",\"password\":\"Passw0rd!\"}")
                .andExpect(status().isCreated());
        hr = login(hrName, "Passw0rd!");
        smtp.purgeEmailFromAllMailboxes();
    }

    // ------------------------------------------------------------------ helpers

    private Candidate candidate(String name, String mobile, String email, boolean withDepartment) {
        Department dept = departments.findByNameIgnoreCase("Engineering").orElseGet(() -> departments.save(new Department("Engineering", null)));
        Candidate c = new Candidate();
        c.setFullName(name);
        c.setMobile(mobile);
        c.setEmail(email);
        if (withDepartment) c.setDepartment(dept);
        c.setApplicationDate(LocalDate.now(IST));
        c.setAppliedAt(Instant.now());
        c.setSource(CandidateSource.MANUAL);
        candidates.saveAndFlush(c);
        CandidateService.assignCode(c);
        return candidates.saveAndFlush(c);
    }

    private Instant schedule(Candidate c) throws Exception {
        Instant at = LocalDate.now(IST).plusDays(3).atTime(11, 30).atZone(IST).toInstant();
        call(post("/api/interviews"), hr, "{\"candidateId\":" + c.getId() + ",\"scheduledAt\":\"" + at + "\",\"durationMinutes\":45,"
                + "\"roundName\":\"Technical Round\",\"interviewer\":\"Panel " + UUID.randomUUID() + "\",\"mode\":\"IN_PERSON\",\"location\":\"" + VENUE + "\"}")
                .andExpect(status().isCreated());
        return at;
    }

    private String expectedBody(String name, Instant at, boolean whatsapp) {
        String date = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH).withZone(IST).format(at);
        return "Dear " + name + ",\n\n"
                + "Greetings from JPM – Junior Processing Mill!\n\n"
                + "Your interview has been scheduled for the Engineering department.\n\n"
                + (whatsapp ? "*Interview Details*" : "Interview Details") + "\n\n"
                + "Date: " + date + "\n"
                + "Time: 11:30 AM\n"
                + "Venue: JPM – Junior Processing Mill\n"
                + "Department: Engineering\n\n"
                + "Please confirm your availability for the interview.\n\n"
                + "Best regards,\n"
                + "HR Department\n"
                + "JPM – Junior Processing Mill";
    }

    // ------------------------------------------------------------------ tests

    @Test
    @Order(1)
    void composesWhatsAppAndEmailFromTheApprovedTemplates() throws Exception {
        Candidate c = candidate("Asha Rao", "98765 43210", "asha.rao@example.com", true);
        Instant at = schedule(c);

        JsonNode compose = json(call(get("/api/candidates/" + c.getId() + "/compose"), hr, null).andExpect(status().isOk()));
        assertThat(compose.get("department").asText()).isEqualTo("Engineering");
        assertThat(compose.at("/interview/venue").asText()).isEqualTo(VENUE);
        assertThat(compose.at("/interview/time").asText()).isEqualTo("11:30 AM");
        assertThat(compose.at("/contact/whatsappNumber").asText()).isEqualTo("+919876543210");

        JsonNode wa = compose.get("whatsapp");
        assertThat(wa.get("ready").asBoolean()).isTrue();
        assertThat(wa.get("message").asText()).isEqualTo(expectedBody("Asha Rao", at, true)).doesNotContain("{{");
        String url = wa.get("url").asText();
        assertThat(url).startsWith("https://wa.me/919876543210?text=").doesNotContain("+").doesNotContain(" ");
        assertThat(URLDecoder.decode(url.substring(url.indexOf("text=") + 5), StandardCharsets.UTF_8)).isEqualTo(expectedBody("Asha Rao", at, true));

        JsonNode em = compose.get("email");
        assertThat(em.get("ready").asBoolean()).isTrue();
        assertThat(em.get("to").asText()).isEqualTo("asha.rao@example.com");
        assertThat(em.get("subject").asText()).isEqualTo("Interview Invitation – JPM");
        assertThat(em.get("body").asText()).isEqualTo(expectedBody("Asha Rao", at, false));
        assertThat(em.get("serviceConfigured").asBoolean()).isTrue();
    }

    @Test
    @Order(2)
    void whatsAppIsRecordedAsPreparedNeverAsDelivered() throws Exception {
        Candidate c = candidate("Ravi Kumar", "+91 91234 56789", "ravi@example.com", true);
        schedule(c);
        JsonNode prepared = json(call(post("/api/candidates/" + c.getId() + "/communications/whatsapp"), hr, "{}").andExpect(status().isOk()));
        assertThat(prepared.at("/communication/status").asText()).isEqualTo("PREPARED");
        assertThat(prepared.at("/communication/templateVersion").asInt()).isEqualTo(1);
        assertThat(prepared.get("url").asText()).startsWith("https://wa.me/919123456789?text=");

        long id = prepared.at("/communication/id").asLong();
        JsonNode marked = json(call(patch("/api/communications/" + id + "/mark-sent"), hr, null).andExpect(status().isOk()));
        assertThat(marked.get("status").asText()).isEqualTo("MARKED_SENT");
        assertThat(marked.get("notes").asText()).contains("delivery is not verified");
        call(patch("/api/communications/" + id + "/mark-sent"), hr, null).andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    void emailIsAcceptedByTheSmtpServerAndRecorded() throws Exception {
        Candidate c = candidate("Meera Iyer", "9000000001", "meera@example.com", true);
        Instant at = schedule(c);
        JsonNode sent = json(call(post("/api/candidates/" + c.getId() + "/communications/email"), hr, "{}").andExpect(status().isOk()));
        assertThat(sent.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(sent.get("providerMessageId").asText()).isNotBlank();
        assertThat(sent.get("provider").asText()).isEqualTo("SMTP localhost");

        MimeMessage[] received = smtp.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Interview Invitation – JPM");
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("meera@example.com");
        assertThat(received[0].getFrom()[0].toString()).contains("hr@jpm.test");
        assertThat(GreenMailUtil.getBody(received[0])).contains("Meera Iyer");
        assertThat(sent.get("content").asText()).isEqualTo(expectedBody("Meera Iyer", at, false));
    }

    @Test
    @Order(4)
    void emailFailureIsRecordedAndCanBeRetried() throws Exception {
        Candidate c = candidate("Karthik Menon", "9000000002", "karthik@example.com", true);
        schedule(c);
        smtp.stop();
        JsonNode failed;
        try {
            failed = json(call(post("/api/candidates/" + c.getId() + "/communications/email"), hr, "{}").andExpect(status().isOk()));
        } finally {
            smtp.start();
        }
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("errorMessage").asText()).contains("email provider").doesNotContain("karthik@example.com");

        JsonNode retried = json(call(post("/api/communications/" + failed.get("id").asLong() + "/retry"), hr, null).andExpect(status().isOk()));
        assertThat(retried.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(retried.get("retryOfId").asLong()).isEqualTo(failed.get("id").asLong());
        assertThat(smtp.getReceivedMessages()).hasSize(1);

        JsonNode history = json(call(get("/api/candidates/" + c.getId() + "/communications"), hr, null).andExpect(status().isOk()));
        assertThat(history).hasSize(2);
        assertThat(history.findValuesAsText("status")).containsExactlyInAnyOrder("FAILED", "ACCEPTED");
        // a successful email cannot be "retried" again
        call(post("/api/communications/" + retried.get("id").asLong() + "/retry"), hr, null).andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void missingOrInvalidContactDetailsBlockTheAction() throws Exception {
        Candidate noEmail = candidate("No Email", "9876500001", null, true);
        schedule(noEmail);
        JsonNode compose = json(call(get("/api/candidates/" + noEmail.getId() + "/compose"), hr, null).andExpect(status().isOk()));
        assertThat(compose.at("/email/ready").asBoolean()).isFalse();
        assertThat(compose.at("/email/problems/0").asText()).isEqualTo("No email address on file");
        call(post("/api/candidates/" + noEmail.getId() + "/communications/email"), hr, "{}").andExpect(status().isBadRequest());

        Candidate badMobile = candidate("Bad Mobile", "12345", "bad.mobile@example.com", true);
        schedule(badMobile);
        compose = json(call(get("/api/candidates/" + badMobile.getId() + "/compose"), hr, null).andExpect(status().isOk()));
        assertThat(compose.at("/whatsapp/ready").asBoolean()).isFalse();
        assertThat(compose.at("/contact/mobileValid").asBoolean()).isFalse();
        call(post("/api/candidates/" + badMobile.getId() + "/communications/whatsapp"), hr, "{}").andExpect(status().isBadRequest());
        call(post("/api/candidates/" + badMobile.getId() + "/communications/call"), hr, null).andExpect(status().isBadRequest());

        Candidate noInterview = candidate("No Interview", "9876500002", "no.interview@example.com", true);
        compose = json(call(get("/api/candidates/" + noInterview.getId() + "/compose"), hr, null).andExpect(status().isOk()));
        assertThat(compose.at("/whatsapp/problems/0").asText()).contains("No upcoming interview");
        assertThat(compose.at("/email/ready").asBoolean()).isFalse();

        Candidate noDept = candidate("No Department", "9876500003", "no.dept@example.com", false);
        schedule(noDept);
        compose = json(call(get("/api/candidates/" + noDept.getId() + "/compose"), hr, null).andExpect(status().isOk()));
        assertThat(compose.at("/email/problems/0").asText()).contains("department");
        call(post("/api/candidates/" + noDept.getId() + "/communications/email"), hr, "{}").andExpect(status().isBadRequest());
        assertThat(smtp.getReceivedMessages()).isEmpty();
    }

    @Test
    @Order(6)
    void callsAreLoggedWithOutcome() throws Exception {
        Candidate c = candidate("Divya Nair", "9876543299", null, true);
        JsonNode started = json(call(post("/api/candidates/" + c.getId() + "/communications/call"), hr, null).andExpect(status().isOk()));
        assertThat(started.get("telUri").asText()).isEqualTo("tel:+919876543299");
        assertThat(started.at("/communication/status").asText()).isEqualTo("CALL_INITIATED");
        JsonNode outcome = json(call(patch("/api/communications/" + started.at("/communication/id").asLong() + "/call-outcome"), hr,
                "{\"outcome\":\"NO_ANSWER\",\"notes\":\"Will try tomorrow\"}").andExpect(status().isOk()));
        assertThat(outcome.get("callOutcome").asText()).isEqualTo("NO_ANSWER");
    }

    @Test
    @Order(7)
    void onlyAdminsManageTemplatesAndAnonymousUsersSeeNothing() throws Exception {
        call(get("/api/communication/templates"), hr, null).andExpect(status().isForbidden());
        call(post("/api/communication/templates"), hr, "{\"channel\":\"WHATSAPP\",\"body\":\"x\"}").andExpect(status().isForbidden());
        call(put("/api/communication/settings"), hr, "{\"companyName\":\"X\",\"defaultVenue\":\"X\",\"hrSignature\":\"X\",\"defaultCountryCode\":\"91\"}")
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/candidates/1/compose")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/candidates/1/communications")).andExpect(status().isUnauthorized());
        call(get("/api/communication/settings"), hr, null).andExpect(status().isOk());
    }

    @Test
    @Order(8)
    void templateChangesCreateVersionsAndKeepSentHistoryUnchanged() throws Exception {
        Candidate c = candidate("Version Test", "9876500009", "version@example.com", true);
        schedule(c);
        JsonNode first = json(call(post("/api/candidates/" + c.getId() + "/communications/whatsapp"), hr, "{}").andExpect(status().isOk()));
        String firstContent = first.at("/communication/content").asText();

        JsonNode overview = json(call(get("/api/communication/templates"), admin, null).andExpect(status().isOk()));
        String original = overview.at("/whatsapp/body").asText();
        int originalVersion = overview.at("/whatsapp/version").asInt();

        // invalid draft is rejected
        call(post("/api/communication/templates"), admin, mapper.writeValueAsString(
                java.util.Map.of("channel", "WHATSAPP", "body", "Hello {{CandidateName}} {{Unknown}}"))).andExpect(status().isBadRequest());

        String changed = original + "\n\nPlease carry a photo ID.";
        JsonNode v2 = json(call(post("/api/communication/templates"), admin, mapper.writeValueAsString(
                java.util.Map.of("channel", "WHATSAPP", "body", changed))).andExpect(status().isOk()));
        assertThat(v2.get("version").asInt()).isEqualTo(originalVersion + 1);

        JsonNode second = json(call(post("/api/candidates/" + c.getId() + "/communications/whatsapp"), hr, "{}").andExpect(status().isOk()));
        assertThat(second.at("/communication/templateVersion").asInt()).isEqualTo(originalVersion + 1);
        assertThat(second.at("/communication/content").asText()).endsWith("Please carry a photo ID.");

        // the earlier record is untouched; candidate rename does not rewrite history either
        Candidate reloaded = candidates.findById(c.getId()).orElseThrow();
        reloaded.setFullName("Renamed Person");
        candidates.saveAndFlush(reloaded);
        JsonNode history = json(call(get("/api/candidates/" + c.getId() + "/communications"), hr, null).andExpect(status().isOk()));
        JsonNode oldRecord = history.get(history.size() - 1);
        assertThat(oldRecord.get("templateVersion").asInt()).isEqualTo(originalVersion);
        assertThat(oldRecord.get("content").asText()).isEqualTo(firstContent);
        assertThat(oldRecord.get("candidateName").asText()).isEqualTo("Version Test");

        // restore the approved wording for any later tests (creates a new version)
        call(post("/api/communication/templates"), admin, mapper.writeValueAsString(java.util.Map.of("channel", "WHATSAPP", "body", original)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ plumbing

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token, String body)
            throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String login(String u, String p) throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }
}
