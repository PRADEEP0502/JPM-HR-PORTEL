package com.jpm.hrms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("false") boolean demoMode,
        @DefaultValue("Asia/Kolkata") String timezone,
        @DefaultValue("http://localhost:5173") List<String> corsAllowedOrigins,
        @DefaultValue("./data/uploads") String uploadDir,
        String documentEncryptionKey,
        @DefaultValue Security security,
        @DefaultValue Mail mail,
        @DefaultValue Google google,
        @DefaultValue Files files,
        @DefaultValue WhatsApp whatsapp) {

    /** loginAttemptsPerIp = sign-in requests allowed per client IP in 5 minutes. */
    public record Security(String jwtSecret,
                           @DefaultValue("43200") long tokenTtlMinutes,
                           String bootstrapAdminUsername,
                           String bootstrapAdminPassword,
                           @DefaultValue("20") int loginAttemptsPerIp) {
    }

    /** Sender identity for outgoing email; SMTP connection settings live under spring.mail.*. */
    public record Mail(String fromAddress, @DefaultValue("JPM HR Department") String fromName) {
    }

    public record Google(String credentialsFile,
                         String spreadsheetId,
                         @DefaultValue("Form Responses 1") String sheetRange,
                         String formUrl) {
    }

    /**
     * Uploaded documents. storage = local (private folder UPLOAD_DIR) or gridfs (inside MongoDB, included in mongodump
     * backups). Only the listed extensions are accepted, and the file content must match the extension.
     */
    public record Files(@DefaultValue("local") String storage,
                        @DefaultValue({"pdf", "jpg", "jpeg", "png", "doc", "docx"}) List<String> allowedExtensions,
                        @DefaultValue MalwareScan malwareScan) {
    }

    /**
     * Malware scanning of uploads. mode = off (development) or clamav (a ClamAV clamd daemon reachable over TCP).
     * failClosed = reject uploads while the scanner is unreachable (recommended in production).
     */
    public record MalwareScan(@DefaultValue("off") String mode,
                              @DefaultValue("localhost") String clamavHost,
                              @DefaultValue("3310") int clamavPort,
                              @DefaultValue("15000") int timeoutMs,
                              @DefaultValue("true") boolean failClosed) {
    }

    /**
     * WhatsApp Business Platform (Meta Cloud API), used for the automatic greeting sent when a candidate submits the
     * Google Form. Off while the access token, phone number ID or template name is empty. Meta only lets a business
     * start a chat with a template it has approved; templateParams lists, in order, the portal values that fill the
     * template's {{1}}, {{2}} ... (CandidateName, CompanyName, Department, Position, HRSignature).
     */
    public record WhatsApp(String accessToken,
                           String phoneNumberId,
                           String templateName,
                           @DefaultValue("en") String templateLanguage,
                           @DefaultValue("CandidateName") List<String> templateParams,
                           @DefaultValue("v23.0") String apiVersion,
                           @DefaultValue("https://graph.facebook.com") String apiBaseUrl) {
    }
}
