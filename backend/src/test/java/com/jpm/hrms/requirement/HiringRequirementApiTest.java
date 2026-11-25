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

        // the candidate queries the page sends for each requirement
        String pending = "NEW,UNDER_REVIEW,SHORTLISTED,INTERVIEW_SCHEDULED,INTERVIEW_COMPLETED,ON_HOLD";
        mvc.perform(get("/api/candidates").header("Authorization", "Bearer " + admin).param("departmentId", String.valueOf(dept)).param("position", "Helper")
                .param("applicationStatus", pending).param("rating", "1-10").param("sort", "rating").param("dir", "desc").param("size", "100")).andExpect(status().isOk());
        mvc.perform(get("/api/candidates").header("Authorization", "Bearer " + admin).param("departmentId", String.valueOf(dept))
                .param("applicationStatus", pending).param("rating", "UNRATED").param("size", "1")).andExpect(status().isOk());
        mvc.perform(get("/api/candidates").header("Authorization", "Bearer " + admin).param("departmentId", String.valueOf(dept))
                .param("applicationStatus", "SELECTED,JOINED").param("size", "1")).andExpect(status().isOk());

        // priority: new ones go last unless a number is given; moving one shifts the others and keeps 1, 2, 3 …
        long wholeDept = idOf(admin, dept, null);
        assertThat(priority(admin, id)).isLessThan(priority(admin, wholeDept));
        call(put("/api/requirements/" + wholeDept), admin, "{\"priority\":1}").andExpect(status().isOk());
        assertThat(priority(admin, wholeDept)).isEqualTo(1);
        assertThat(priority(admin, id)).isGreaterThan(1);
        long third = mapper.readTree(call(post("/api/requirements"), admin, "{\"departmentId\":" + dept + ",\"position\":\"Third\",\"needed\":1,\"priority\":1}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(priority(admin, third)).isEqualTo(1);
        assertThat(priority(admin, wholeDept)).isEqualTo(2);
        assertThat(list(admin).get(0).get("hired").asLong()).isGreaterThanOrEqualTo(0);
        call(put("/api/requirements/" + id), admin, "{}").andExpect(status().isBadRequest());

        call(put("/api/requirements/" + id), admin, "{\"needed\":8}").andExpect(status().isOk());
        assertThat(needed(admin, id)).isEqualTo(8);

        // keep pending: hiring paused with a reason and who did it; resume clears it; the number and priority stay
        JsonNode paused = mapper.readTree(call(put("/api/requirements/" + id), admin, "{\"pending\":true,\"pendingReason\":\" Budget next month \"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(paused.get("pending").asBoolean()).isTrue();
        assertThat(paused.get("pendingReason").asText()).isEqualTo("Budget next month");
        assertThat(paused.get("pendingBy").asText()).isEqualTo("admin");
        assertThat(paused.get("pendingSince").isNull()).isFalse();
        assertThat(needed(admin, id)).isEqualTo(8);
        JsonNode resumed = mapper.readTree(call(put("/api/requirements/" + id), admin, "{\"pending\":false}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(resumed.get("pending").asBoolean()).isFalse();
        assertThat(resumed.get("pendingReason").isNull()).isTrue();

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

    private JsonNode list(String token) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/requirements").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private int priority(String token, long id) throws Exception {
        JsonNode all = list(token);
        for (int i = 0; i < all.size(); i++) {
            JsonNode r = all.get(i);
            if (r.get("id").asLong() == id) {
                assertThat(r.get("priority").asInt()).isEqualTo(i + 1); // the list is in priority order with no gaps
                return r.get("priority").asInt();
            }
        }
        return -1;
    }

    private long idOf(String token, long dept, String position) throws Exception {
        for (JsonNode r : list(token)) {
            if (r.get("departmentId").asLong() == dept && (position == null ? r.get("position").isNull() : position.equals(r.get("position").asText()))) return r.get("id").asLong();
        }
        return -1;
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
