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
        @DefaultValue Google google) {

    public record Security(String jwtSecret,
                           @DefaultValue("480") long tokenTtlMinutes,
                           String bootstrapAdminUsername,
                           String bootstrapAdminPassword) {
    }

    /** Sender identity for outgoing email; SMTP connection settings live under spring.mail.*. */
    public record Mail(String fromAddress, @DefaultValue("JPM HR Department") String fromName) {
    }

    public record Google(String credentialsFile,
                         String spreadsheetId,
                         @DefaultValue("Form Responses 1") String sheetRange,
                         String formUrl) {
    }
}
