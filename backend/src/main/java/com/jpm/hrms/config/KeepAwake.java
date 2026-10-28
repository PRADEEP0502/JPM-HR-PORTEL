package com.jpm.hrms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Keeps a free Render instance awake: Render sleeps a service after 15 minutes without inbound traffic, so the app
 * calls its own public health URL every 10 minutes (the request goes out and back in through Render).
 * Render sets RENDER_EXTERNAL_URL automatically; elsewhere set KEEP_AWAKE_URL, or leave both empty to disable.
 */
@Component
public class KeepAwake {
    private static final Logger log = LoggerFactory.getLogger(KeepAwake.class);
    private final String url;
    private final RestClient client;

    public KeepAwake(@Value("${app.keep-awake-url:}") String baseUrl) {
        this.url = StringUtils.hasText(baseUrl) ? baseUrl.replaceAll("/+$", "") + "/actuator/health" : null;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.client = RestClient.builder().requestFactory(factory).build();
        if (url != null) log.info("Keep-awake enabled: pinging {} every 10 minutes", url);
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 600_000)
    public void ping() {
        if (url == null) return;
        try {
            client.get().uri(url).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Keep-awake ping failed: {}", e.getMessage());
        }
    }
}
