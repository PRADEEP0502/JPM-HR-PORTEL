package com.jpm.hrms.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the map position from what a candidate pastes: a Google Maps link (full or short, e.g. maps.app.goo.gl/...),
 * or plain coordinates such as "11.0423, 77.3194". Short and place links are opened on Google's own servers only.
 */
@Component
public class MapLinkResolver {
    private static final Logger log = LoggerFactory.getLogger(MapLinkResolver.class);

    public static final String HOW_TO = "In Google Maps, long-press the home to drop a pin (or tap the blue dot when at home), "
            + "tap Share, copy the link and paste it here";

    /** The outcome: a point, or the reason there is none. */
    public record Result(GeoPoint point, String problem) {
        static Result of(GeoPoint p) {
            return new Result(p, null);
        }

        static Result fail(String problem) {
            return new Result(null, problem);
        }

        public boolean found() {
            return point != null;
        }
    }

    private static final String LAT = "(-?\\d{1,2}\\.\\d+)";
    private static final String LNG = "(-?\\d{1,3}\\.\\d+)";
    private static final String SEP = "\\s*(?:,|%2C)\\s*\\+?";
    /** In order of precision: the place's own pin, an explicit query/marker, then the map's centre. */
    private static final List<Pattern> URL_PATTERNS = List.of(
            Pattern.compile("!3d" + LAT + "!4d" + LNG),
            Pattern.compile("[?&](?:q|query|ll|destination|daddr|sll|center)=(?:loc:)?" + LAT + SEP + LNG, Pattern.CASE_INSENSITIVE),
            Pattern.compile("/maps/search/" + LAT + SEP + LNG),
            Pattern.compile("@" + LAT + "," + LNG));
    private static final Pattern PLAIN = Pattern.compile("^\\s*\\(?" + LAT + "\\s*[,\\s]\\s*" + LNG + "\\)?\\s*$");
    private static final Pattern GOOGLE_HOST = Pattern.compile("^(?:www\\.|maps\\.)?google\\.(?:com|co\\.in|co\\.[a-z]{2}|[a-z]{2,3})$");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public Result resolve(String input) {
        if (!StringUtils.hasText(input)) return Result.fail("Nothing was entered");
        String text = input.trim();
        Matcher plain = PLAIN.matcher(text);
        if (plain.matches()) return point(plain.group(1), plain.group(2)).map(Result::of).orElseGet(() -> Result.fail("Those are not valid coordinates"));

        Matcher link = Pattern.compile("https?://\\S+").matcher(text);
        if (!link.find()) return Result.fail("This is not a Google Maps link. " + HOW_TO);
        String url = link.group().replaceAll("[).,;]+$", "");
        Optional<GeoPoint> direct = fromUrl(url);
        if (direct.isPresent()) return Result.of(direct.get());
        if (!allowedHost(url)) return Result.fail("Only Google Maps links can be read. " + HOW_TO);
        try {
            return open(url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("Reading the link was interrupted");
        } catch (IOException | IllegalArgumentException e) {
            log.info("Could not open a map link: {}", e.getClass().getSimpleName());
            return Result.fail("Google Maps could not be reached to read this link - try again, or paste the coordinates");
        }
    }

    /**
     * Follows short-link redirects (Google hosts only) and reads the position written in the address. The page itself
     * is never read: Google centres it on the visitor's own region (our server is abroad), not on the place.
     */
    private Result open(String url) throws IOException, InterruptedException {
        String current = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(current)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0").header("Accept-Language", "en").GET().build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = res.body()) {
                int status = res.statusCode();
                if (status >= 300 && status < 400) {
                    String next = res.headers().firstValue("Location").orElse(null);
                    if (next == null) break;
                    current = URI.create(current).resolve(next).toString();
                    Optional<GeoPoint> p = fromUrl(current);
                    if (p.isPresent()) return Result.of(p.get());
                    if (!allowedHost(current)) break;
                    continue;
                }
                if (status == 200 && current.contains("/maps/place/")) {
                    return Result.fail("This link is for a named place, and Google does not put its exact position in it. " + HOW_TO);
                }
                break;
            }
        }
        return Result.fail("Couldn't find the location in this link. " + HOW_TO);
    }

    static Optional<GeoPoint> fromUrl(String url) {
        String decoded;
        try {
            decoded = URLDecoder.decode(url.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            decoded = url;
        }
        for (Pattern p : URL_PATTERNS) {
            for (String candidate : List.of(url, decoded)) {
                Matcher m = p.matcher(candidate);
                if (m.find()) {
                    Optional<GeoPoint> point = point(m.group(1), m.group(2));
                    if (point.isPresent()) return point;
                }
            }
        }
        return Optional.empty();
    }

    static boolean allowedHost(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(u.getScheme())
                    && (host.equals("maps.app.goo.gl") || host.equals("goo.gl") || GOOGLE_HOST.matcher(host).matches());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Optional<GeoPoint> point(String lat, String lng) {
        try {
            double a = Double.parseDouble(lat);
            double b = Double.parseDouble(lng);
            return GeoPoint.valid(a, b) ? Optional.of(new GeoPoint(a, b)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
