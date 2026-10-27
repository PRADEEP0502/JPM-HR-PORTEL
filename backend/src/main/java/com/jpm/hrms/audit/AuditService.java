package com.jpm.hrms.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Append-only audit trail for security-relevant and business-relevant changes. */
@Service
public class AuditService {
    public static final String SYSTEM_ACTOR = "system";

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(String action, String entityType, Long entityId, String details) {
        recordAs(currentActor(), action, entityType, entityId, details);
    }

    public void recordAs(String actor, String action, String entityType, Long entityId, String details) {
        String safeDetails = details == null ? null : details.length() > 2000 ? details.substring(0, 1997) + "..." : details;
        repo.save(new AuditLog(Instant.now(), actor, action, entityType, entityId, safeDetails));
    }

    public static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getName() != null ? auth.getName() : SYSTEM_ACTOR;
    }
}
