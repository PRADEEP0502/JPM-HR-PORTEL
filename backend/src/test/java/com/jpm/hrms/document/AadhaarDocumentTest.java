package com.jpm.hrms.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.candidate.CandidateService;
import com.jpm.hrms.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AadhaarDocumentTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    CandidateDocumentRepository documents;
    @Autowired
    AppProperties props;

    @Test
    void aadhaarRequiresConsentIsEncryptedAtRestAndCanBeDeleted() throws Exception {
        Candidate c = new Candidate();
        c.setFullName("Aadhaar Test");
        c.setEmail("aadhaar.test@example.com");
        c.setApplicationDate(LocalDate.now());
        c.setAppliedAt(Instant.now());
        c.setSource(CandidateSource.MANUAL);
        candidates.save(c);
        CandidateService.assignCode(c, candidates);
        candidates.save(c);

        String token = login();
        byte[] pdf = "%PDF-1.4 sample aadhaar copy for test".getBytes(StandardCharsets.ISO_8859_1);
        MockMultipartFile file = new MockMultipartFile("file", "aadhaar.pdf", "application/pdf", pdf);

        // Without consent -> rejected
        mvc.perform(multipart("/api/candidates/" + c.getId() + "/documents").file(file).param("type", "AADHAAR")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // With consent -> stored, flagged encrypted
        String body = mvc.perform(multipart("/api/candidates/" + c.getId() + "/documents").file(file)
                        .param("type", "AADHAAR").param("consent", "true").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.docType").value("AADHAAR"))
                .andExpect(jsonPath("$.encrypted").value(true))
                .andReturn().getResponse().getContentAsString();
        long docId = mapper.readTree(body).get("id").asLong();

        // On disk: not the plaintext
        CandidateDocument d = documents.findById(docId).orElseThrow();
        byte[] onDisk = Files.readAllBytes(Path.of(props.uploadDir()).toAbsolutePath().normalize().resolve(d.getStorageRef()));
        assertThat(new String(onDisk, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");

        // Download: original bytes
        byte[] downloaded = mvc.perform(get("/api/documents/" + docId + "/download").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(pdf);

        // Delete: row and file gone
        mvc.perform(delete("/api/documents/" + docId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        assertThat(documents.findById(docId)).isEmpty();
        assertThat(Files.exists(Path.of(props.uploadDir()).toAbsolutePath().normalize().resolve(d.getStorageRef()))).isFalse();
    }

    private String login() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }
}
