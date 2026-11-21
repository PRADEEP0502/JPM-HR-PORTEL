package com.jpm.hrms.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** After start-up (and after the settings exist), re-reads stored map pins once if the reading rules changed. */
@Component
@Order(10)
class PinRecheck implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PinRecheck.class);
    private final HomeDistanceService homes;

    PinRecheck(HomeDistanceService homes) {
        this.homes = homes;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(() -> {
            try {
                homes.recheckIfRulesChanged();
            } catch (RuntimeException e) {
                log.warn("Re-checking stored map pins failed: {}", e.getClass().getSimpleName());
            }
        }, "map-pin-recheck");
        t.setDaemon(true);
        t.start();
    }
}
