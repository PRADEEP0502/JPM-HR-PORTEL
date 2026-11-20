package com.jpm.hrms.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jpm.hrms.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends approved WhatsApp template messages through the WhatsApp Business Platform (Meta Cloud API).
 * A send counts as successful only when Meta accepts the message and returns its id; delivery is not reported here.
 * The access token and recipient numbers are never written to the logs.
 */
@Component
public class WhatsAppCloudSender {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudSender.class);
    public static final String PROVIDER = "WhatsApp Cloud API";

    public static class WhatsAppException extends RuntimeException {
        public WhatsAppException(String message) {
            super(message);
        }
    }

    /** Plain-language reasons for the errors Meta returns most often. */
    private static final Map<Integer, String> KNOWN_ERRORS = Map.of(
            190, "the WhatsApp access token is invalid or has expired - create a permanent token and update WHATSAPP_ACCESS_TOKEN",
            131030, "this number is not in the test recipient list - add it in Meta's WhatsApp API setup, or use a live business number",
            131026, "the message could not be delivered to this number (it may not be on WhatsApp)",
            132001, "the template name or language does not match an approved template in WhatsApp Manager",
            132000, "the number of template values does not match the template - check WHATSAPP_TEMPLATE_PARAMS",
            132015, "the template is paused or disabled in WhatsApp Manager",
            131042, "there is a payment problem on the WhatsApp Business account",
            131056, "too many messages to this number in a short time - try again later");

    private final AppProperties.WhatsApp cfg;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WhatsAppCloudSender(AppProperties props, ObjectMapper mapper) {
        this.cfg = props.whatsapp();
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(cfg.accessToken()) && StringUtils.hasText(cfg.phoneNumberId()) && StringUtils.hasText(cfg.templateName());
    }

    public String templateName() {
        return StringUtils.hasText(cfg.templateName()) ? cfg.templateName().trim() : null;
    }

    public String templateLanguage() {
        return StringUtils.hasText(cfg.templateLanguage()) ? cfg.templateLanguage().trim() : "en";
    }

    /** Portal placeholder names (without braces) for the template's {{1}}, {{2}} ... in order. */
    public List<String> templateParams() {
        return cfg.templateParams() == null ? List.of()
                : cfg.templateParams().stream().map(p -> p.replaceAll("[{}\\s]", "")).filter(p -> !p.isEmpty()).toList();
    }

    /** Sends the configured template to an international number (digits only) and returns Meta's message id. */
    public String sendTemplate(String toInternational, List<String> values) {
        if (!isConfigured()) throw new WhatsAppException("WhatsApp sending is not configured on the server");
        String url = cfg.apiBaseUrl().replaceAll("/+$", "") + "/" + cfg.apiVersion() + "/" + cfg.phoneNumberId().trim() + "/messages";
        HttpResponse<String> res;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + cfg.accessToken().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(toInternational, values), StandardCharsets.UTF_8))
                    .build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhatsAppException("Sending was interrupted");
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Could not reach the WhatsApp Cloud API: {}", e.getClass().getSimpleName());
            throw new WhatsAppException("Could not reach the WhatsApp Cloud API (" + e.getClass().getSimpleName() + ")");
        }
        JsonNode json = parse(res.body());
        if (res.statusCode() / 100 == 2) {
            String id = json.path("messages").path(0).path("id").asText(null);
            if (id != null) return id;
            throw new WhatsAppException("WhatsApp did not return a message id (HTTP " + res.statusCode() + ")");
        }
        throw new WhatsAppException(describeError(res.statusCode(), json));
    }

    String body(String to, List<String> values) {
        ObjectNode root = mapper.createObjectNode();
        root.put("messaging_product", "whatsapp");
        root.put("recipient_type", "individual");
        root.put("to", to);
        root.put("type", "template");
        ObjectNode template = root.putObject("template");
        template.put("name", templateName());
        template.putObject("language").put("code", templateLanguage());
        if (!values.isEmpty()) {
            ObjectNode bodyComponent = template.putArray("components").addObject();
            bodyComponent.put("type", "body");
            ArrayNode params = bodyComponent.putArray("parameters");
            for (String v : values) params.addObject().put("type", "text").put("text", v);
        }
        return root.toString();
    }

    private JsonNode parse(String body) {
        try {
            return body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
        } catch (IOException e) {
            return mapper.createObjectNode();
        }
    }

    static String describeError(int status, JsonNode json) {
        JsonNode err = json.path("error");
        int code = err.path("code").asInt(0);
        String known = KNOWN_ERRORS.get(code);
        String detail = err.path("error_data").path("details").asText(err.path("message").asText(""));
        // keep it short and free of phone numbers
        detail = detail.replaceAll("\\+?\\d[\\d\\s-]{8,}\\d", "<number>");
        if (detail.length() > 300) detail = detail.substring(0, 300);
        String reason = known != null ? known : !detail.isBlank() ? detail : "HTTP " + status;
        log.warn("WhatsApp Cloud API rejected a message: HTTP {}, code {}", status, code);
        return "WhatsApp rejected the message: " + reason + (code != 0 ? " (code " + code + ")" : "");
    }
}
