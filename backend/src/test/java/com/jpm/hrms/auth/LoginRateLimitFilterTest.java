package com.jpm.hrms.auth;

import com.jpm.hrms.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTest {

    @Test
    void limitsSignInAttemptsPerAddressOnly() throws Exception {
        var props = new AppProperties(false, "Asia/Kolkata", List.of(), "./target/x", null,
                new AppProperties.Security(null, 480, null, null, 3), new AppProperties.Mail(null, "HR"),
                new AppProperties.Google(null, null, "Form Responses 1", null),
                new AppProperties.Files("local", List.of("pdf"), new AppProperties.MalwareScan("off", "localhost", 3310, 1000, true)));
        LoginRateLimitFilter filter = new LoginRateLimitFilter(props);

        for (int i = 1; i <= 3; i++) assertThat(login(filter, "203.0.113.5").getStatus()).isEqualTo(200);
        MockHttpServletResponse blocked = login(filter, "203.0.113.5");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getContentAsString()).contains("Too many sign-in attempts");

        assertThat(login(filter, "198.51.100.7").getStatus()).isEqualTo(200); // another address is unaffected
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/candidates");
        other.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(other, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200); // only sign-in is limited
    }

    private static MockHttpServletResponse login(LoginRateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }
}
