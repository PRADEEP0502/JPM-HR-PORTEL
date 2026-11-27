package com.jpm.hrms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps a free Render instance awake during office hours only. Render sleeps a service after 15 minutes without
 * inbound traffic, so the app calls its own public health URL every 10 minutes (out and back in through Render) while
 * the local time is inside KEEP_AWAKE_HOURS (default 08-21, Asia/Kolkata). Outside those hours it is allowed to sleep:
 * Render's free plan allows about 750 running hours a month, and running all day every day uses nearly all of them.
 * A sleeping service wakes on the next request (about a minute). Render sets RENDER_EXTERNAL_URL automatically;
 * elsewhere set KEEP_AWAKE_URL, or leave both empty to disable. KEEP_AWAKE_HOURS=00-24 keeps it awake all day.
 */
@Component
public class KeepAwake {
    private static final Logger log = LoggerFactory.getLogger(KeepAwake.class);
    private static final Pattern HOURS = Pattern.compile("^\\s*(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s*$");

    private final String url;
    private final RestClient client;
    private final int fromHour;
    private final int toHour;
    private final Clock clock;
    private final ZoneId zone;

    public KeepAwake(@Value("${app.keep-awake-url:}") String baseUrl, @Value("${app.keep-awake-hours:08-21}") String hours,
                     Clock clock, ZoneId businessZone) {
        this.url = StringUtils.hasText(baseUrl) ? baseUrl.replaceAll("/+$", "") + "/actuator/health" : null;
        int[] window = parseHours(hours);
        this.fromHour = window[0];
        this.toHour = window[1];
        this.clock = clock;
        this.zone = businessZone;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.client = RestClient.builder().requestFactory(factory).build();
        if (url != null) {
            log.info("Keep-awake enabled: pinging {} every 10 minutes between {}:00 and {}:00 ({})", url, fromHour, toHour, zone);
        }
    }

    /** "08-21" → [8, 21]; anything unreadable falls back to office hours 8-21. */
    static int[] parseHours(String text) {
        Matcher m = HOURS.matcher(text == null ? "" : text);
        if (m.matches()) {
            int from = Integer.parseInt(m.group(1));
            int to = Integer.parseInt(m.group(2));
            if (from >= 0 && to <= 24 && from < to) return new int[]{from, to};
        }
        return new int[]{8, 21};
    }

    /** Whether the service should be kept awake at this local time. */
    boolean awakeAt(LocalTime time) {
        int h = time.getHour();
        return h >= fromHour && h < toHour;
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 600_000)
    public void ping() {
        if (url == null || !awakeAt(LocalTime.now(clock.withZone(zone)))) return;
        try {
            client.get().uri(url).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Keep-awake ping failed: {}", e.getMessage());
        }
    }
}
