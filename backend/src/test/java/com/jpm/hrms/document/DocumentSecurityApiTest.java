package com.jpm.hrms.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.candidate.CandidateService;
import com.jpm.hrms.document.scan.MalwareScanner;
import com.jpm.hrms.document.scan.MalwareScanner.Outcome;
import com.jpm.hrms.document.scan.MalwareScanner.Verdict;
import com.jpm.hrms.document.storage.GridFsFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload allow-list, content checks, malware scan outcomes, inline viewing and GridFS storage. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentSecurityApiTest {
    private static final byte[] PDF = "%PDF-1.4 resume".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    CandidateDocumentRepository documents;
    @Autowired
    GridFsFileStorage gridFs;
    @MockitoBean
    MalwareScanner scanner;

    @Test
    void uploadRulesScanOutcomesAndInlineViewing() throws Exception {
        String token = login();
        Candidate c = candidate();
        String url = "/api/candidates/" + c.getId() + "/documents";
        when(scanner.scan(any())).thenReturn(new Verdict(Outcome.CLEAN, null));

        // Not on the allow-list (DOCX is not accepted by default), and content that does not match the extension
        upload(url, token, "cv.docx", new byte[]{'P', 'K', 3, 4, 0}).andExpect(status().isBadRequest());
        upload(url, token, "cv.pdf", "MZ this is an exe".getBytes(StandardCharsets.ISO_8859_1)).andExpect(status().isBadRequest());
        upload(url, token, "../../etc/passwd.pdf", PDF).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("passwd.pdf"))
                .andExpect(jsonPath("$.scanStatus").value("CLEAN"));

        // Infected files are rejected and never stored; a scanner outage pauses uploads (fail closed)
        long before = documents.findByCandidateIdOrderByCreatedAtDesc(c.getId()).size();
        when(scanner.scan(any())).thenReturn(new Verdict(Outcome.INFECTED, "Eicar-Test-Signature"));
        upload(url, token, "virus.pdf", PDF).andExpect(status().isBadRequest());
        when(scanner.scan(any())).thenReturn(new Verdict(Outcome.ERROR, "unreachable"));
        upload(url, token, "later.pdf", PDF).andExpect(status().isServiceUnavailable());
        assertThat(documents.findByCandidateIdOrderByCreatedAtDesc(c.getId())).hasSize((int) before);

        // Viewer: PDF / PNG inline with their real type; plain download stays an attachment
        when(scanner.scan(any())).thenReturn(new Verdict(Outcome.CLEAN, null));
        long pngId = mapper.readTree(upload(url, token, "photo.png", PNG).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/documents/" + pngId + "/download").param("inline", "true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
        mvc.perform(get("/api/documents/" + pngId + "/download").header("Authorization", "Bearer " + token))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        // Not signed in: no file
        mvc.perform(get("/api/documents/" + pngId + "/download")).andExpect(status().isUnauthorized());
    }

    @Test
    void gridFsStorageRoundTrip() throws Exception {
        String key = gridFs.store("99", "pdf", PDF);
        assertThat(gridFs.exists(key)).isTrue();
        assertThat(gridFs.read(key)).isEqualTo(PDF);
        gridFs.delete(key);
        assertThat(gridFs.exists(key)).isFalse();
        assertThat(gridFs.exists("not-an-id")).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions upload(String url, String token, String name, byte[] content) throws Exception {
        return mvc.perform(multipart(url).file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .param("type", "RESUME").header("Authorization", "Bearer " + token));
    }

    private Candidate candidate() {
        Candidate c = new Candidate();
        c.setFullName("Document Security Test");
        c.setApplicationDate(LocalDate.now());
        c.setAppliedAt(Instant.now());
        c.setSource(CandidateSource.MANUAL);
        candidates.save(c);
        CandidateService.assignCode(c, candidates);
        return candidates.save(c);
    }

    private String login() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }
}
