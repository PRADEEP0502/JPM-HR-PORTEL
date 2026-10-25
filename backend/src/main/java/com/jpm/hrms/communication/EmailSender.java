package com.jpm.hrms.communication;

import com.jpm.hrms.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;

/**
 * Sends email through the configured SMTP provider (Gmail, Microsoft 365, SES, SendGrid ...).
 * A send counts as successful only when the provider accepts the message without error.
 * Recipient addresses are deliberately never written to the logs.
 */
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    public static class EmailException extends RuntimeException {
        public EmailException(String message) {
            super(message);
        }
    }

    private final ObjectProvider<JavaMailSender> mailSender;
    private final Environment env;
    private final AppProperties props;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender, Environment env, AppProperties props) {
        this.mailSender = mailSender;
        this.env = env;
        this.props = props;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(env.getProperty("spring.mail.host")) && fromAddress() != null
                && mailSender.getIfAvailable() != null;
    }

    public String providerName() {
        String host = env.getProperty("spring.mail.host");
        return StringUtils.hasText(host) ? "SMTP " + host : null;
    }

    /** Explicit MAIL_FROM, otherwise the SMTP username when it is an email address. */
    public String fromAddress() {
        String from = props.mail().fromAddress();
        if (StringUtils.hasText(from)) return from.trim();
        String user = env.getProperty("spring.mail.username");
        return StringUtils.hasText(user) && user.contains("@") ? user.trim() : null;
    }

    public String fromName() {
        return props.mail().fromName();
    }

    /** Sends a plain-text email (with an equivalent HTML part) and returns the Message-ID. */
    public String send(String to, String subject, String text) {
        if (!isConfigured()) throw new EmailException("Email service is not configured on the server");
        JavaMailSender sender = mailSender.getObject();
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress(), fromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, toHtml(text));
            sender.send(msg);
            return msg.getMessageID();
        } catch (MailAuthenticationException e) {
            log.warn("Email provider rejected the SMTP credentials");
            throw new EmailException("The email provider rejected the configured credentials");
        } catch (MailSendException e) {
            log.warn("Email provider did not accept the message: {}", rootMessage(e));
            throw new EmailException("The email provider did not accept the message (" + rootMessage(e) + ")");
        } catch (MailException e) {
            log.warn("Email sending failed: {}", rootMessage(e));
            throw new EmailException("Could not connect to the email provider (" + rootMessage(e) + ")");
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new EmailException("Could not build the email message");
        }
    }

    static String toHtml(String text) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.6;color:#1f2328\">"
                + HtmlUtils.htmlEscape(text).replace("\r\n", "\n").replace("\n", "<br>") + "</div>";
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        // keep it short and free of addresses
        m = m.replaceAll("[\\w.+-]+@[\\w.-]+", "<address>");
        return m.length() > 300 ? m.substring(0, 300) : m;
    }
}
