package com.jpm.hrms.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.jpm.hrms.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal server-side client for the Google Sheets v4 and Drive v3 REST APIs, authenticated with a
 * service-account key read from the server's filesystem. Credentials and access tokens never leave
 * this class and are never returned to the browser.
 */
@Component
public class GoogleApiClient {
    private static final Logger log = LoggerFactory.getLogger(GoogleApiClient.class);
    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/spreadsheets.readonly",
            "https://www.googleapis.com/auth/drive.readonly");

    public static class GoogleApiException extends RuntimeException {
        public GoogleApiException(String message) {
            super(message);
        }
    }

    public record DriveFile(String name, String mimeType, Long size) {
    }

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile GoogleCredentials credentials;
    private volatile String clientEmail;

    public GoogleApiClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        String file = props.google().credentialsFile();
        return StringUtils.hasText(file) && Files.isReadable(Path.of(file));
    }

    /** The service account's e-mail (not a secret) - the sheet must be shared with it. */
    public String serviceAccountEmail() {
        if (!isConfigured()) return null;
        try {
            credentials();
            return clientEmail;
        } catch (GoogleApiException e) {
            return null;
        }
    }

    private GoogleCredentials credentials() {
        if (credentials == null) {
            synchronized (this) {
                if (credentials == null) {
                    if (!isConfigured()) {
                        throw new GoogleApiException("Google service-account credentials are not configured on the server "
                                + "(set GOOGLE_SERVICE_ACCOUNT_FILE).");
                    }
                    try (InputStream in = Files.newInputStream(Path.of(props.google().credentialsFile()))) {
                        GoogleCredentials c = GoogleCredentials.fromStream(in).createScoped(SCOPES);
                        if (c instanceof ServiceAccountCredentials sa) clientEmail = sa.getClientEmail();
                        credentials = c;
                    } catch (IOException e) {
                        log.error("Could not read Google credentials file", e);
                        throw new GoogleApiException("The Google credentials file could not be read. Check that it is a valid service-account JSON key.");
                    }
                }
            }
        }
        return credentials;
    }

    private String accessToken() {
        try {
            GoogleCredentials c = credentials();
            c.refreshIfExpired();
            return c.getAccessToken().getTokenValue();
        } catch (IOException e) {
            log.error("Google token refresh failed", e);
            throw new GoogleApiException("Could not authenticate with Google using the configured service account.");
        }
    }

    /** Reads raw cell values (unformatted; dates as serial numbers so they are locale-independent). */
    public List<List<Object>> readValues(String spreadsheetId, String range) {
        String url = "https://sheets.googleapis.com/v4/spreadsheets/" + encode(spreadsheetId) + "/values/" + encode(range)
                + "?majorDimension=ROWS&valueRenderOption=UNFORMATTED_VALUE&dateTimeRenderOption=SERIAL_NUMBER";
        JsonNode body = getJson(url, "spreadsheet");
        List<List<Object>> rows = new ArrayList<>();
        for (JsonNode rowNode : body.path("values")) {
            List<Object> row = new ArrayList<>();
            for (JsonNode cell : rowNode) {
                if (cell.isNumber()) row.add(cell.decimalValue());
                else if (cell.isBoolean()) row.add(cell.booleanValue() ? "Yes" : "No");
                else row.add(cell.asText());
            }
            rows.add(row);
        }
        return rows;
    }

    public String spreadsheetTitle(String spreadsheetId) {
        String url = "https://sheets.googleapis.com/v4/spreadsheets/" + encode(spreadsheetId) + "?fields=properties.title";
        return getJson(url, "spreadsheet").path("properties").path("title").asText(null);
    }

    public DriveFile driveMetadata(String fileId) {
        String url = "https://www.googleapis.com/drive/v3/files/" + encode(fileId) + "?fields=name,mimeType,size&supportsAllDrives=true";
        JsonNode n = getJson(url, "file");
        return new DriveFile(n.path("name").asText("document"), n.path("mimeType").asText("application/octet-stream"),
                n.hasNonNull("size") ? n.get("size").asLong() : null);
    }

    public InputStream driveDownload(String fileId) {
        String url = "https://www.googleapis.com/drive/v3/files/" + encode(fileId) + "?alt=media&supportsAllDrives=true";
        try {
            HttpResponse<InputStream> res = http.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                res.body().close();
                throw new GoogleApiException(describe(res.statusCode(), "file"));
            }
            return res.body();
        } catch (IOException e) {
            throw new GoogleApiException("Could not reach Google Drive: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleApiException("Interrupted while downloading from Google Drive");
        }
    }

    private JsonNode getJson(String url, String what) {
        try {
            HttpResponse<String> res = http.send(request(url), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Google API {} returned {}: {}", url.replaceAll("\\?.*", ""), res.statusCode(), abbreviate(res.body()));
                throw new GoogleApiException(describe(res.statusCode(), what));
            }
            return mapper.readTree(res.body());
        } catch (IOException e) {
            throw new GoogleApiException("Could not reach Google APIs: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleApiException("Interrupted while calling Google APIs");
        }
    }

    private HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + accessToken()).GET().build();
    }

    private String describe(int status, String what) {
        String email = clientEmail == null ? "the service account" : clientEmail;
        return switch (status) {
            case 400 -> "Google rejected the request. Check the sheet/tab name in the range setting.";
            case 401 -> "Google authentication failed. Check the service-account key.";
            case 403 -> "Access denied to the " + what + ". Share it (Viewer) with " + email
                    + " and make sure the Sheets/Drive APIs are enabled for the project.";
            case 404 -> "The " + what + " was not found. Check the configured ID.";
            case 429 -> "Google API rate limit reached. Try again shortly.";
            default -> "Google API error (HTTP " + status + ").";
        };
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String abbreviate(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
