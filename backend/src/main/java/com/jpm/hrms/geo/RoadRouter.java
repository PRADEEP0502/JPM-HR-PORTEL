package com.jpm.hrms.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Road distance and driving time by car between two points. Uses Google's Routes API when GOOGLE_MAPS_API_KEY is set
 * (the same numbers as Google Maps), otherwise the free OpenStreetMap route server (OSRM), which is usually close.
 */
@Component
public class RoadRouter {
    private static final Logger log = LoggerFactory.getLogger(RoadRouter.class);
    public static final String GOOGLE = "Google Maps";
    public static final String OSM = "OpenStreetMap";

    public record Route(double km, int minutes, String source) {
    }

    private final String googleKey;
    private final String osrmUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    /** The public OSRM server allows about one request a second. */
    private long lastOsrmCall;

    public RoadRouter(@Value("${app.maps.google-api-key:}") String googleKey,
                      @Value("${app.maps.osrm-url:https://router.project-osrm.org}") String osrmUrl, ObjectMapper mapper) {
        this.googleKey = googleKey == null ? "" : googleKey.trim();
        this.osrmUrl = osrmUrl.replaceAll("/+$", "");
        this.mapper = mapper;
    }

    public String source() {
        return StringUtils.hasText(googleKey) ? GOOGLE : OSM;
    }

    /** The route by car, or empty when the service cannot be reached or finds no road. */
    public Optional<Route> route(GeoPoint from, GeoPoint to) {
        try {
            return StringUtils.hasText(googleKey) ? google(from, to) : osrm(from, to);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            log.info("Road distance lookup failed ({}): {}", source(), e.getClass().getSimpleName());
            return Optional.empty();
        }
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
