package com.jpm.hrms.auth;

import com.jpm.hrms.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits sign-in attempts per client IP (on top of the per-username lockout in {@link LoginAttemptService}), so one
 * address cannot try many usernames. In-memory: limits apply per backend instance and reset on restart.
 * Behind a reverse proxy the client IP comes from X-Forwarded-For only for trusted internal proxies
 * (server.forward-headers-strategy=native).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoginRateLimitFilter extends OncePerRequestFilter {
    static final Duration WINDOW = Duration.ofMinutes(5);
    private final int maxPerWindow;

    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(AppProperties props) {
        this.maxPerWindow = props.security().loginAttemptsPerIp();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = Instant.now();
        if (windows.size() > 10_000) windows.values().removeIf(w -> w.start().plus(WINDOW).isBefore(now));
        Window w = windows.compute(request.getRemoteAddr(), (ip, old) ->
                old == null || old.start().plus(WINDOW).isBefore(now) ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (w.count() > maxPerWindow) {
            long retry = Math.max(1, Duration.between(now, w.start().plus(WINDOW)).toSeconds());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retry));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many sign-in attempts from this network. Try again in a few minutes.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
