package com.jpm.hrms.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileRatingApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CandidateRepository candidates;
    @Autowired
    HrRemarkRepository remarks;

    @Test
    void ratingIsValidatedSavedFilteredAndSorted() throws Exception {
        String token = login();
        Candidate high = candidate("Rating Test High");
        Candidate low = candidate("Rating Test Low");
        Candidate unrated = candidate("Rating Test Unrated");

        // Only whole numbers 1-10
        for (String bad : List.of("0", "11", "7.5", "7.0", "null", "\"abc\"")) {
            mvc.perform(rate(high.getId(), token, "{\"rating\":" + bad + "}")).andExpect(status().isBadRequest());
        }
        assertThat(candidates.findById(high.getId()).orElseThrow().getProfileRating()).isNull();

        // Not signed in -> refused
        mvc.perform(patch("/api/candidates/" + high.getId() + "/rating").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5}")).andExpect(status().isUnauthorized());

        mvc.perform(rate(high.getId(), token, "{\"rating\":9,\"remark\":\"Strong Java skills\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileRating").value(9))
                .andExpect(jsonPath("$.ratingRemark").value("Strong Java skills"))
                .andExpect(jsonPath("$.ratedBy").value("admin"));
        mvc.perform(rate(low.getId(), token, "{\"rating\":3}")).andExpect(status().isOk());
        // Update an existing rating
        mvc.perform(rate(low.getId(), token, "{\"rating\":4}")).andExpect(jsonPath("$.profileRating").value(4));
        // Same value again -> nothing to change
        mvc.perform(rate(low.getId(), token, "{\"rating\":4}")).andExpect(status().isBadRequest());

        // Remark goes to HR remarks history; candidate row and code untouched
        assertThat(remarks.findByCandidateIdOrderByCreatedAtDesc(high.getId()))
                .anyMatch(r -> r.getRemark().equals("Profile match rating 9/10: Strong Java skills"));
        Candidate reloaded = candidates.findById(high.getId()).orElseThrow();
        assertThat(reloaded.getCandidateCode()).isEqualTo(high.getCandidateCode());
        assertThat(reloaded.getFullName()).isEqualTo("Rating Test High");

        // Filters
        assertThat(names(token, "rating", "7+")).containsExactly("Rating Test High");
        assertThat(names(token, "rating", "1-4")).containsExactly("Rating Test Low");
        assertThat(names(token, "rating", "UNRATED")).containsExactly("Rating Test Unrated");
        mvc.perform(get("/api/candidates").param("rating", "0-12").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Sorting: highest first and lowest first, unrated always last
        assertThat(names(token, "sort", "rating", "dir", "desc")).containsExactly("Rating Test High", "Rating Test Low", "Rating Test Unrated");
        assertThat(names(token, "sort", "rating", "dir", "asc")).containsExactly("Rating Test Low", "Rating Test High", "Rating Test Unrated");

        // Audited
        mvc.perform(get("/api/candidates/" + high.getId()).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.activity[0].action").value("RATING_UPDATED"));
    }

    private Candidate candidate(String name) {
        Candidate c = new Candidate();
        c.setFullName(name);
        c.setEmail(name.replace(' ', '.').toLowerCase() + "@example.com");
        c.setApplicationDate(LocalDate.now());
        c.setAppliedAt(Instant.now());
        c.setSource(CandidateSource.MANUAL);
        candidates.save(c);
        CandidateService.assignCode(c, candidates);
        return candidates.save(c);
    }

    private org.springframework.test.web.servlet.RequestBuilder rate(Long id, String token, String json) {
        return patch("/api/candidates/" + id + "/rating").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private List<String> names(String token, String... params) throws Exception {
        var req = get("/api/candidates").param("q", "Rating Test").param("size", "50").header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        String body = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> out = new ArrayList<>();
        for (JsonNode n : mapper.readTree(body).get("content")) out.add(n.get("fullName").asText());
        return out;
    }

    private String login() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }
}
