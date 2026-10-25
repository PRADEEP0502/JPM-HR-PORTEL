package com.jpm.hrms.notification;

import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.interview.InterviewRepository;
import com.jpm.hrms.interview.InterviewService;
import com.jpm.hrms.sync.SyncRun;
import com.jpm.hrms.sync.SyncRunRepository;
import com.jpm.hrms.user.AppUser;
import com.jpm.hrms.user.AppUserRepository;
import com.jpm.hrms.user.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Notifications are derived from live data (new applications, upcoming interviews, sync failures). */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class NotificationController {

    public record Notification(String id, String type, String title, String message, Instant at, String link, boolean unread) {
    }

    public record NotificationList(long unreadCount, List<Notification> items) {
    }

    private final CandidateRepository candidates;
    private final InterviewRepository interviews;
    private final SyncRunRepository syncRuns;
    private final AppUserRepository users;
    private final Clock clock;
    private final DateTimeFormatter time;

    public NotificationController(CandidateRepository candidates, InterviewRepository interviews, SyncRunRepository syncRuns,
                                  AppUserRepository users, Clock clock, ZoneId businessZone) {
        this.candidates = candidates;
        this.interviews = interviews;
        this.syncRuns = syncRuns;
        this.users = users;
        this.clock = clock;
        this.time = DateTimeFormatter.ofPattern("dd MMM, hh:mm a").withZone(businessZone);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public NotificationList list(Authentication auth) {
        AppUser me = users.findByUsernameIgnoreCase(auth.getName()).orElseThrow(() -> ApiException.notFound("User"));
        Instant now = clock.instant();
        Instant seen = me.getNotificationsSeenAt() == null ? Instant.EPOCH : me.getNotificationsSeenAt();
        List<Notification> items = new ArrayList<>();

        for (var c : candidates.findTop10ByCreatedAtAfterOrderByCreatedAtDesc(now.minus(Duration.ofDays(7)))) {
            items.add(new Notification("cand-" + c.getId(), "NEW_CANDIDATE", "New application",
                    c.getFullName() + " (" + c.getCandidateCode() + ") applied", c.getCreatedAt(),
                    "/candidates/" + c.getId(), c.getCreatedAt().isAfter(seen)));
        }
        for (var i : interviews.findByStatusInAndScheduledAtBetweenOrderByScheduledAtAsc(InterviewService.ACTIVE, now, now.plus(Duration.ofHours(24)))) {
            items.add(new Notification("int-" + i.getId(), "UPCOMING_INTERVIEW", "Interview within 24 hours",
                    i.getCandidate().getFullName() + " - " + i.getRoundName() + " at " + time.format(i.getScheduledAt()),
                    i.getUpdatedAt(), "/interviews", i.getUpdatedAt().isAfter(seen)));
        }
        if (me.getRole() == Role.ADMIN) {
            for (var r : syncRuns.findTop5ByStatusAndStartedAtAfterOrderByStartedAtDesc(SyncRun.Status.FAILED, now.minus(Duration.ofDays(3)))) {
                items.add(new Notification("sync-" + r.getId(), "SYNC_FAILED", "Google Sheets sync failed",
                        r.getErrorMessage() == null ? "See Settings → Integration" : r.getErrorMessage(),
                        r.getStartedAt(), "/settings?tab=integration", r.getStartedAt().isAfter(seen)));
            }
        }
        items.sort(Comparator.comparing(Notification::at).reversed());
        return new NotificationList(items.stream().filter(Notification::unread).count(), items);
    }

    @PostMapping("/read")
    @Transactional
    public void markRead(Authentication auth) {
        users.findByUsernameIgnoreCase(auth.getName()).ifPresent(u -> u.setNotificationsSeenAt(clock.instant()));
    }
}
