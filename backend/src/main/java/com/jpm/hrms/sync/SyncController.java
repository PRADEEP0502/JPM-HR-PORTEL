package com.jpm.hrms.sync;

import com.jpm.hrms.sync.SyncService.SettingsRequest;
import com.jpm.hrms.sync.SyncService.SettingsResponse;
import com.jpm.hrms.sync.SyncService.SyncRunResponse;
import com.jpm.hrms.sync.SyncService.SyncStatus;
import com.jpm.hrms.sync.SyncService.TestResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    /** Last sync time / state - visible to HR so they know how fresh the data is. Errors are admin-only. */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public SyncStatus status(Authentication auth) {
        SyncStatus s = service.status();
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return admin ? s : new SyncStatus(s.configured(), s.credentialsConfigured(), s.spreadsheetConfigured(),
                s.autoSyncEnabled(), s.syncIntervalMinutes(), s.running(), s.formUrl(), s.lastSyncAt(), s.lastStatus(),
                s.lastSuccessAt(), null);
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public SyncRunResponse run(Authentication auth) {
        return service.runNow(auth.getName());
    }

    @GetMapping("/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SyncRunResponse> runs(@RequestParam(defaultValue = "20") int limit) {
        return service.recentRuns(limit);
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponse settings() {
        return service.getSettings();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponse update(@Valid @RequestBody SettingsRequest req, Authentication auth) {
        return service.updateSettings(req, auth.getName());
    }

    /** Creates (or replaces) the key the Google Form script sends with each response. */
    @PostMapping("/push-key")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponse newPushKey(Authentication auth) {
        return service.newPushKey(auth.getName());
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public TestResult test() {
        return service.testConnection();
    }
}
