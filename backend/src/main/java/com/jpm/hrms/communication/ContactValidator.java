package com.jpm.hrms.communication;

import com.jpm.hrms.candidate.FieldParsers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Validates candidate contact details before any communication action is offered. */
public final class ContactValidator {
    private ContactValidator() {
    }

    /** International number as digits only (country code included, no '+'), or the reason it is unusable. */
    public record PhoneCheck(boolean valid, String international, String display, String reason) {
        static PhoneCheck ok(String digits) {
            return new PhoneCheck(true, digits, "+" + digits, null);
        }

        static PhoneCheck bad(String reason) {
            return new PhoneCheck(false, null, null, reason);
        }
    }

    public record EmailCheck(boolean valid, String address, String reason) {
    }

    /**
     * 10-digit numbers are treated as local numbers and get the default country code. For India
     * (91) a mobile number must start with 6-9. International numbers must be 11-15 digits.
     */
    public static PhoneCheck checkPhone(String raw, String defaultCountryCode) {
        if (raw == null || raw.isBlank()) return PhoneCheck.bad("No mobile number on file");
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("\\D", "");
        String cc = defaultCountryCode == null ? "91" : defaultCountryCode.replaceAll("\\D", "");
        String international;
        if (trimmed.startsWith("+") || trimmed.startsWith("00")) {
            international = trimmed.startsWith("00") ? digits.substring(2) : digits;
        } else if (digits.length() == 10) {
            international = cc + digits;
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            international = cc + digits.substring(1);
        } else {
            international = digits;
        }
        if (international.length() < 11 || international.length() > 15) {
            return PhoneCheck.bad("Mobile number \"" + raw + "\" is not a valid number with country code");
        }
        if (international.startsWith("91")) {
            String local = international.substring(2);
            if (local.length() != 10 || "6789".indexOf(local.charAt(0)) < 0) {
                return PhoneCheck.bad("Mobile number \"" + raw + "\" is not a valid Indian mobile number");
            }
        }
        return PhoneCheck.ok(international);
    }

    public static EmailCheck checkEmail(String raw) {
        if (raw == null || raw.isBlank()) return new EmailCheck(false, null, "No email address on file");
        String e = raw.trim();
        return FieldParsers.isValidEmail(e) ? new EmailCheck(true, e, null)
                : new EmailCheck(false, null, "Email address \"" + raw + "\" is not valid");
    }

    /** WhatsApp click-to-chat link. Spaces are encoded as %20 (not '+', which WhatsApp shows literally). */
    public static String whatsAppUrl(String internationalDigits, String message) {
        return "https://wa.me/" + internationalDigits + "?text="
                + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
