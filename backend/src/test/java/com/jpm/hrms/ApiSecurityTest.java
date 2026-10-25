package com.jpm.hrms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityTest extends PostgresTestSupport {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/candidates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/info")).andExpect(status().isOk());
    }

    @Test
    void loginAndRoleChecks() throws Exception {
        String token = login("admin", "Admin@12345");
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.totalCandidates").isNumber());

        // Admin creates an HR user; HR cannot reach admin-only endpoints.
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hr.test\",\"fullName\":\"HR Test\",\"role\":\"HR\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isCreated());
        String hr = login("hr.test", "Passw0rd!");
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + hr)).andExpect(status().isForbidden());
        mvc.perform(get("/api/sync/settings").header("Authorization", "Bearer " + hr)).andExpect(status().isForbidden());
        mvc.perform(get("/api/candidates").header("Authorization", "Bearer " + hr)).andExpect(status().isOk());
    }

    @Test
    void rejectsBadCredentialsAndTamperedTokens() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/candidates").header("Authorization", "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());
    }

    private String login(String u, String p) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode n = mapper.readTree(body);
        return n.get("token").asText();
    }
}
