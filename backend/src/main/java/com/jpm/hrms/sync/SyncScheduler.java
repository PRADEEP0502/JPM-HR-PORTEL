package com.jpm.hrms.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks once a minute whether an automatic sync is due (interval is admin-configurable). */
@Component
public class SyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);
    private final SyncService sync;

    public SyncScheduler(SyncService sync) {
        this.sync = sync;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void tick() {
        try {
            sync.runIfDue();
        } catch (RuntimeException e) {
            log.error("Scheduled sync check failed", e);
        }
    }
}
