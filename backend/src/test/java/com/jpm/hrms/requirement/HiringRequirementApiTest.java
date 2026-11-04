package com.jpm.hrms.requirement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Saved hiring requirements: add, change the number, delete, and who may do it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HiringRequirementApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void requirementLifecycle() throws Exception {
        String admin = login("admin", "Admin@12345");
        long dept = mapper.readTree(call(post("/api/departments"), admin, "{\"name\":\"Requirement Test Dept\",\"active\":true,\"positions\":[\"Helper\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        JsonNode created = mapper.readTree(call(post("/api/requirements"), admin, "{\"departmentId\":" + dept + ",\"position\":\" Helper \",\"needed\":5}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(created.get("departmentName").asText()).isEqualTo("Requirement Test Dept");
        assertThat(created.get("position").asText()).isEqualTo("Helper");
        long id = created.get("id").asLong();

        // the same department and sub-work (any case) cannot be added twice; the whole department is a separate requirement
        call(post("/api/requirements"), admin, "{\"departmentId\":" + dept + ",\"position\":\"helper\",\"needed\":2}").andExpect(status().isConflict());
        call(post("/api/requirements"), admin, "{\"departmentId\":" + dept + ",\"needed\":2}").andExpect(status().isCreated());
        // invalid number, unknown department
        call(post("/api/requirements"), admin, "{\"departmentId\":" + dept + ",\"position\":\"Other\",\"needed\":0}").andExpect(status().isBadRequest());
        call(post("/api/requirements"), admin, "{\"departmentId\":999999,\"needed\":1}").andExpect(status().isNotFound());

        call(put("/api/requirements/" + id), admin, "{\"needed\":8}").andExpect(status().isOk());
        assertThat(needed(admin, id)).isEqualTo(8);

        // HR manages requirements too; interviewers and anonymous callers do not
        call(post("/api/users"), admin, "{\"username\":\"hr.req\",\"fullName\":\"HR Req\",\"role\":\"HR\",\"password\":\"HrReq@12345\"}").andExpect(status().is2xxSuccessful());
        call(post("/api/users"), admin, "{\"username\":\"iv.req\",\"fullName\":\"IV Req\",\"role\":\"INTERVIEWER\",\"password\":\"IvReq@12345\"}").andExpect(status().is2xxSuccessful());
        call(put("/api/requirements/" + id), login("hr.req", "HrReq@12345"), "{\"needed\":3}").andExpect(status().isOk());
        assertThat(needed(admin, id)).isEqualTo(3);
        mvc.perform(get("/api/requirements").header("Authorization", "Bearer " + login("iv.req", "IvReq@12345"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/requirements")).andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/requirements/" + id).header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        assertThat(needed(admin, id)).isEqualTo(-1);
    }

    private int needed(String token, long id) throws Exception {
        JsonNode all = mapper.readTree(mvc.perform(get("/api/requirements").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode r : all) if (r.get("id").asLong() == id) return r.get("needed").asInt();
        return -1;
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String login(String username, String password) throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }
}
