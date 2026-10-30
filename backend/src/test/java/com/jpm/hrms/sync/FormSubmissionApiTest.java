package com.jpm.hrms.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.document.CandidateDocument.DocType;
import com.jpm.hrms.document.CandidateDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Google Form responses pushed by the form's Apps Script, without the service account. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormSubmissionApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    CandidateDocumentRepository documents;

    private static final List<String> HEADERS = List.of("Timestamp", "Full Name", "Mobile Number", "Email", "Department",
            "Position (Production)", "Upload Resume", "Declaration");

    @Test
    void formResponseBecomesCandidateOnce() throws Exception {
        String admin = login();
        String key = json(mvc.perform(post("/api/sync/push-key").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())).get("formPushKey").asText();
        assertThat(key).hasSizeGreaterThan(20);

        List<Object> row = List.of("2026-10-30 10:15:00", "Form Pushed Applicant", "98765 11001", "form.pushed@example.com",
                "Production", "Helper", "https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz", "I confirm");
        String pdf = Base64.getEncoder().encodeToString("%PDF-1.4\n% test resume\n".getBytes(StandardCharsets.US_ASCII));
        List<Map<String, String>> files = List.of(Map.of("field", "RESUME", "name", "resume.pdf", "data", pdf));

        submit("wrong-key", row, files).andExpect(status().isUnauthorized());

        JsonNode created = json(submit(key, row, files).andExpect(status().isOk()));
        assertThat(created.get("status").asText()).isEqualTo("CREATED");
        String code = created.get("candidateCode").asText();
        Candidate c = candidates.findAll().stream().filter(x -> code.equals(x.getCandidateCode())).findFirst().orElseThrow();
        assertThat(c.getSource()).isEqualTo(CandidateSource.GOOGLE_SHEETS);
        assertThat(c.getMobile()).isEqualTo("9876511001");
        assertThat(c.getPosition()).isEqualTo("Helper");
        // the uploaded file is stored; the Drive link is not added as a second copy
        var docs = documents.findByCandidateIdOrderByCreatedAtDesc(c.getId());
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getDocType()).isEqualTo(DocType.RESUME);

        // the same response sent again, and a new response with the same mobile, add nothing
        assertThat(json(submit(key, row, files)).get("status").asText()).isEqualTo("ALREADY_IMPORTED");
        List<Object> again = new java.util.ArrayList<>(row);
        again.set(0, "2026-10-30 11:00:00");
        again.set(3, "other.mail@example.com");
        assertThat(json(submit(key, again, List.of())).get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(candidates.findAll().stream().filter(x -> "9876511001".equals(x.getMobile())).count()).isEqualTo(1);
    }

    private ResultActions submit(String key, List<Object> values, List<Map<String, String>> files) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", key);
        body.put("headers", HEADERS);
        body.put("values", values);
        body.put("files", files);
        return mvc.perform(post("/api/public/form-submissions").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String login() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }
}
