package com.jpm.hrms.auth;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory brute-force protection: after 5 failed attempts a username is
 * locked for 15 minutes. (For multi-instance deployments, move this to a shared store.)
 */
@Service
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record Attempts(int count, Instant firstFailure) {
    }

    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        Attempts a = failures.get(key(username));
        if (a == null) return false;
        if (a.firstFailure().plus(LOCK_DURATION).isBefore(Instant.now())) {
            failures.remove(key(username));
            return false;
        }
        return a.count() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String username) {
        failures.compute(key(username), (k, a) -> a == null || a.firstFailure().plus(LOCK_DURATION).isBefore(Instant.now())
                ? new Attempts(1, Instant.now())
                : new Attempts(a.count() + 1, a.firstFailure()));
    }

    public void recordSuccess(String username) {
        failures.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
