package com.jpm.hrms.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.hrms.communication.CommunicationSettings;
import com.jpm.hrms.communication.CommunicationSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Road distance and driving time by car between two points, from (in this order) Google's Routes API when
 * GOOGLE_MAPS_API_KEY is set, the free Google Maps Apps Script web app linked in Settings (google-form/road-distance.gs),
 * or the free OpenStreetMap route server (OSRM). The first two give the same numbers as Google Maps.
 */
@Component
public class RoadRouter {
    private static final Logger log = LoggerFactory.getLogger(RoadRouter.class);
    public static final String GOOGLE = "Google Maps";
    public static final String OSM = "OpenStreetMap";

    public record Route(double km, int minutes, String source) {
    }

    /** Apps Script web-app links: https://script.google.com/macros/s/<id>/exec */
    public static final java.util.regex.Pattern SCRIPT_URL =
            java.util.regex.Pattern.compile("^https://script\\.google\\.com/macros/s/[A-Za-z0-9_-]{20,}/exec$");

    private final String googleKey;
    private final CommunicationSettingsRepository settingsRepo;
    private final String osrmUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    /** Apps Script answers with a redirect to script.googleusercontent.com. */
    private final HttpClient scriptHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    /** The public OSRM server allows about one request a second. */
    private long lastOsrmCall;

    public RoadRouter(@Value("${app.maps.google-api-key:}") String googleKey,
                      @Value("${app.maps.osrm-url:https://router.project-osrm.org}") String osrmUrl, ObjectMapper mapper,
                      CommunicationSettingsRepository settingsRepo) {
        this.googleKey = googleKey == null ? "" : googleKey.trim();
        this.settingsRepo = settingsRepo;
        this.osrmUrl = osrmUrl.replaceAll("/+$", "");
        this.mapper = mapper;
    }

    /** The service road distances should come from now. */
    public String source() {
        return StringUtils.hasText(googleKey) || scriptUrl() != null ? GOOGLE : OSM;
    }

    private String scriptUrl() {
        return settingsRepo.findById(CommunicationSettings.SINGLETON_ID).map(CommunicationSettings::getRoadScriptUrl)
                .filter(StringUtils::hasText).orElse(null);
    }

    /**
     * The route by car, or empty when no service can be reached or finds a road. When Google cannot answer (for
     * example its daily allowance is used up) OpenStreetMap is used, and Google is tried again later.
     */
    public Optional<Route> route(GeoPoint from, GeoPoint to) {
        try {
            Optional<Route> r = Optional.empty();
            if (StringUtils.hasText(googleKey)) {
                r = google(from, to);
            } else {
                String script = scriptUrl();
                if (script != null) r = script(script, from, to).route();
            }
            return r.isPresent() ? r : osrm(from, to);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            log.info("Road distance lookup failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private record ScriptAnswer(Optional<Route> route, String error) {
    }

    /** Null when the Apps Script link answers with a road distance; otherwise what went wrong. */
    public String checkScript(String url, GeoPoint from, GeoPoint to) {
        if (!SCRIPT_URL.matcher(url).matches()) {
            return "paste the Web app URL from Apps Script (Deploy → Manage deployments), like https://script.google.com/macros/s/…/exec";
        }
        try {
            ScriptAnswer a = script(url, from, to);
            return a.route().isPresent() ? null : a.error();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "the check was interrupted";
        } catch (IOException | RuntimeException e) {
            return "the script could not be reached (" + e.getClass().getSimpleName() + ")";
        }
    }

    private ScriptAnswer script(String url, GeoPoint from, GeoPoint to) throws IOException, InterruptedException {
        if (!SCRIPT_URL.matcher(url).matches()) return new ScriptAnswer(Optional.empty(), "not an Apps Script web-app link");
        String q = String.format(Locale.ROOT, "?from=%.6f,%.6f&to=%.6f,%.6f", from.lat(), from.lng(), to.lat(), to.lng());
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + q)).timeout(Duration.ofSeconds(25)).GET().build();
        HttpResponse<String> res = scriptHttp.send(req, HttpResponse.BodyHandlers.ofString());
        String body = res.body() == null ? "" : res.body().trim();
        if (res.statusCode() != 200 || !body.startsWith("{")) {
            // a sign-in page instead of JSON means the deployment is not open to "Anyone"
            return new ScriptAnswer(Optional.empty(), body.startsWith("<")
                    ? "the script asks for a sign-in - deploy it with \"Who has access: Anyone\""
                    : "the script answered HTTP " + res.statusCode());
        }
        JsonNode json = mapper.readTree(body);
        if (json.hasNonNull("error")) return new ScriptAnswer(Optional.empty(), "Google Maps answered: " + json.get("error").asText());
        if (!json.has("distanceMeters")) return new ScriptAnswer(Optional.empty(), "the script gave no distance - check it is road-distance.gs");
        return new ScriptAnswer(Optional.of(new Route(round(json.get("distanceMeters").asDouble() / 1000),
                minutes(json.path("durationSeconds").asLong()), GOOGLE)), null);
    }

    private Optional<Route> google(GeoPoint from, GeoPoint to) throws IOException, InterruptedException {
        String body = "{\"origin\":" + waypoint(from) + ",\"destination\":" + waypoint(to)
                + ",\"travelMode\":\"DRIVE\",\"routingPreference\":\"TRAFFIC_UNAWARE\",\"units\":\"METRIC\"}";
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", googleKey)
                .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.warn("Google Routes API answered HTTP {}", res.statusCode());
            return Optional.empty();
        }
        JsonNode route = mapper.readTree(res.body()).path("routes").path(0);
        if (!route.has("distanceMeters")) return Optional.empty();
        // duration is like "843s"
        String digits = route.path("duration").asText("").replaceAll("[^0-9]", "");
        long seconds = digits.isEmpty() ? 0 : Long.parseLong(digits);
        return Optional.of(new Route(round(route.path("distanceMeters").asDouble() / 1000), minutes(seconds), GOOGLE));
    }

    private synchronized Optional<Route> osrm(GeoPoint from, GeoPoint to) throws IOException, InterruptedException {
        long wait = lastOsrmCall + 1100 - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        lastOsrmCall = System.currentTimeMillis();
        String url = String.format(Locale.ROOT, "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",
                osrmUrl, from.lng(), from.lat(), to.lng(), to.lat());
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "JPM-HR-Portal/1.0").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.info("OSRM answered HTTP {}", res.statusCode());
            return Optional.empty();
        }
        JsonNode json = mapper.readTree(res.body());
        JsonNode route = json.path("routes").path(0);
        if (!"Ok".equals(json.path("code").asText()) || !route.has("distance")) return Optional.empty();
        return Optional.of(new Route(round(route.path("distance").asDouble() / 1000), minutes(route.path("duration").asLong()), OSM));
    }

    private static String waypoint(GeoPoint p) {
        return String.format(Locale.ROOT, "{\"location\":{\"latLng\":{\"latitude\":%.7f,\"longitude\":%.7f}}}", p.lat(), p.lng());
    }

    private static double round(double km) {
        return Math.round(km * 10) / 10.0;
    }

    private static int minutes(long seconds) {
        return (int) Math.max(1, Math.round(seconds / 60.0));
    }
}
