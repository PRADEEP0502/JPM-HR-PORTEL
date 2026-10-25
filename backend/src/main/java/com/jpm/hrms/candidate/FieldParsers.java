package com.jpm.hrms.candidate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lenient parsing + strict validation of candidate fields coming from free-text form answers.
 * Every parser returns {@code null} when the value cannot be understood - callers decide whether
 * that is an error (required field) or a data-quality warning (optional field).
 */
public final class FieldParsers {
    private FieldParsers() {
    }

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern YEARS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:years?|yrs?|y)\\b");
    private static final Pattern MONTHS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:months?|mons?|mths?|m)\\b");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH));
    /** Google Sheets serial dates count days from 1899-12-30. */
    private static final LocalDate SHEETS_EPOCH = LocalDate.of(1899, 12, 30);

    public static String clean(Object raw) {
        if (raw == null) return null;
        String s = raw instanceof Number n ? new BigDecimal(n.toString()).stripTrailingZeros().toPlainString() : raw.toString();
        s = s.replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? null : s;
    }

    public static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= 200 && EMAIL.matcher(email).matches();
    }

    /**
     * Normalises a phone number to digits with an optional leading '+'. A 12-digit Indian number
     * starting with 91 is reduced to the 10-digit form. Returns null if it is not 10-15 digits.
     */
    public static String normalizeMobile(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        boolean plus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) return digits.substring(1);
        if (digits.length() == 10) return digits;
        if (digits.length() >= 11 && digits.length() <= 15) return (plus ? "+" : "") + digits;
        return null;
    }

    /** Accepts Sheets serial numbers and common written formats (day-first for ambiguous numeric dates). */
    public static LocalDate parseDate(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) {
            double serial = n.doubleValue();
            if (serial < 1 || serial > 2958465) return null;
            return SHEETS_EPOCH.plusDays((long) Math.floor(serial));
        }
        String s = clean(raw);
        if (s == null) return null;
        if (s.contains(" ") && s.matches("^\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4} .*")) s = s.substring(0, s.indexOf(' '));
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f.withResolverStyle(ResolverStyle.STRICT));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    /**
     * Converts answers like "3", "2.5 years", "1 year 6 months", "8 months", "Fresher" into years
     * (one decimal). Returns null when no number can be found or the value is out of range.
     */
    public static BigDecimal parseExperience(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return inRange(new BigDecimal(n.toString()));
        String s = clean(raw);
        if (s == null) return null;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("fresher") || lower.equals("nil") || lower.equals("none") || lower.equals("no")) return BigDecimal.ZERO.setScale(1);
        Matcher y = YEARS.matcher(lower);
        Matcher m = MONTHS.matcher(lower);
        boolean hasYears = y.find();
        boolean hasMonths = m.find();
        if (hasYears || hasMonths) {
            BigDecimal years = hasYears ? new BigDecimal(y.group(1)) : BigDecimal.ZERO;
            if (hasMonths) years = years.add(new BigDecimal(m.group(1)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP));
            return inRange(years);
        }
        Matcher num = NUMBER.matcher(lower);
        return num.find() ? inRange(new BigDecimal(num.group(1))) : null;
    }

    public static Integer parseYear(Object raw, int maxYear) {
        String s = clean(raw);
        if (s == null) return null;
        Matcher m = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(s);
        if (!m.find()) return null;
        int year = Integer.parseInt(m.group(1));
        return year >= 1950 && year <= maxYear ? year : null;
    }

    private static BigDecimal inRange(BigDecimal years) {
        if (years.signum() < 0 || years.compareTo(BigDecimal.valueOf(60)) > 0) return null;
        return years.setScale(1, RoundingMode.HALF_UP);
    }
}
