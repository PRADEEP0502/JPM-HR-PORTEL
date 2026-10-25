package com.jpm.hrms.sync;

import com.jpm.hrms.candidate.FieldParsers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure (no I/O) translation of Google Form response rows into candidate data. */
public final class SheetRowMapper {
    private SheetRowMapper() {
    }

    private static final Pattern DRIVE_ID = Pattern.compile("(?:[?&]id=|/d/)([-\\w]{20,})");
    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
            DateTimeFormatter.ofPattern("M/d/uuuu H:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/uuuu H:mm:ss"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd H:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/uuuu, H:mm:ss"));

    public record ColumnResolution(Map<SheetField, Integer> columns, List<String> unmappedHeaders, List<String> problems) {
    }

    public record ParsedRow(int rowNumber, String rowKey, Instant appliedAt, LocalDate applicationDate,
                            String fullName, LocalDate dateOfBirth, String mobile, String email, String address,
                            String qualification, String institution, Integer passingYear, String previousCompany,
                            BigDecimal experienceYears, String previousDesignation, String skills, String expectedSalary,
                            String department, String referenceName, String referenceRelation, String referenceContact,
                            String familyRefName, String familyRefRelation, String familyRefContact,
                            List<String> resumeIds, List<String> aadhaarIds, List<String> certificateIds, List<String> otherDocIds,
                            List<String> errors, List<String> warnings) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * Maps sheet headers to fields: admin overrides first, then exact alias matches, then
     * prefix matches (e.g. "Full Name (as per certificates)").
     */
    public static ColumnResolution resolveColumns(List<Object> headerRow, Map<String, String> overrides) {
        List<String> headers = headerRow.stream().map(h -> h == null ? "" : h.toString().trim()).toList();
        Map<SheetField, Integer> cols = new EnumMap<>(SheetField.class);
        Set<Integer> used = new HashSet<>();
        List<String> problems = new ArrayList<>();

        if (overrides != null) {
            overrides.forEach((key, header) -> {
                SheetField f;
                try {
                    f = SheetField.valueOf(key);
                } catch (IllegalArgumentException e) {
                    problems.add("Unknown field in column mapping: " + key);
                    return;
                }
                if (header == null || header.isBlank()) return;
                int idx = indexOfNormalized(headers, SheetField.normalize(header));
                if (idx < 0) problems.add("Mapped header \"" + header + "\" for " + f.label + " was not found in the sheet");
                else {
                    cols.put(f, idx);
                    used.add(idx);
                }
            });
        }
        for (SheetField f : SheetField.values()) {
            if (cols.containsKey(f)) continue;
            for (String alias : f.aliases) {
                int idx = indexOfNormalized(headers, SheetField.normalize(alias));
                if (idx >= 0 && !used.contains(idx)) {
                    cols.put(f, idx);
                    used.add(idx);
                    break;
                }
            }
        }
        for (SheetField f : SheetField.values()) {
            if (cols.containsKey(f)) continue;
            int best = -1;
            int bestLen = 0;
            for (String alias : f.aliases) {
                String a = SheetField.normalize(alias);
                if (a.length() < 4) continue;
                for (int i = 0; i < headers.size(); i++) {
                    if (used.contains(i)) continue;
                    if (SheetField.normalize(headers.get(i)).startsWith(a) && a.length() > bestLen) {
                        best = i;
                        bestLen = a.length();
                    }
                }
            }
            if (best >= 0) {
                cols.put(f, best);
                used.add(best);
            }
        }
        if (!cols.containsKey(SheetField.TIMESTAMP)) problems.add("No \"Timestamp\" column found - is this a Google Form response sheet?");
        if (!cols.containsKey(SheetField.FULL_NAME)) problems.add("No candidate name column found. Map \"Full name\" in the column mapping.");
        if (!cols.containsKey(SheetField.MOBILE) && !cols.containsKey(SheetField.EMAIL)) {
            problems.add("Neither a mobile number nor an email column was found.");
        }
        List<String> unmapped = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) if (!used.contains(i) && !headers.get(i).isBlank()) unmapped.add(headers.get(i));
        return new ColumnResolution(cols, unmapped, problems);
    }

    public static ParsedRow parse(List<Object> row, int rowNumber, Map<SheetField, Integer> cols, ZoneId zone, LocalDate today) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Object tsRaw = cell(row, cols, SheetField.TIMESTAMP);
        Instant appliedAt = parseTimestamp(tsRaw, zone);
        if (appliedAt == null) {
            errors.add("missing or unreadable Timestamp");
            appliedAt = today.atStartOfDay(zone).toInstant();
        }
        LocalDate applicationDate = appliedAt.atZone(zone).toLocalDate();

        String fullName = FieldParsers.truncate(text(row, cols, SheetField.FULL_NAME), 150);
        if (fullName == null) errors.add("full name is missing");

        String mobileRaw = text(row, cols, SheetField.MOBILE);
        String mobile = FieldParsers.normalizeMobile(mobileRaw);
        if (mobileRaw != null && mobile == null) warnings.add("invalid mobile number \"" + mobileRaw + "\"");

        String emailRaw = text(row, cols, SheetField.EMAIL);
        String email = emailRaw == null ? null : emailRaw.toLowerCase(Locale.ROOT);
        if (email != null && !FieldParsers.isValidEmail(email)) {
            warnings.add("invalid email \"" + emailRaw + "\"");
            email = null;
        }
        if (mobile == null && email == null) errors.add("no valid mobile number or email");

        Object dobRaw = cell(row, cols, SheetField.DATE_OF_BIRTH);
        LocalDate dob = FieldParsers.parseDate(dobRaw);
        if (FieldParsers.clean(dobRaw) != null) {
            if (dob == null) warnings.add("unreadable date of birth \"" + FieldParsers.clean(dobRaw) + "\"");
            else if (dob.isAfter(today.minusYears(14)) || dob.isBefore(today.minusYears(80))) {
                warnings.add("implausible date of birth " + dob);
                dob = null;
            }
        }

        Object expRaw = cell(row, cols, SheetField.EXPERIENCE);
        BigDecimal exp = FieldParsers.parseExperience(expRaw);
        if (FieldParsers.clean(expRaw) != null && exp == null) warnings.add("unreadable experience \"" + FieldParsers.clean(expRaw) + "\"");

        Object yearRaw = cell(row, cols, SheetField.PASSING_YEAR);
        Integer passingYear = FieldParsers.parseYear(yearRaw, today.getYear() + 1);
        if (FieldParsers.clean(yearRaw) != null && passingYear == null) warnings.add("invalid passing year \"" + FieldParsers.clean(yearRaw) + "\"");

        List<String> resumeIds = driveIds(text(row, cols, SheetField.RESUME));
        List<String> aadhaarIds = driveIds(text(row, cols, SheetField.AADHAAR));
        List<String> certIds = driveIds(text(row, cols, SheetField.CERTIFICATES));
        List<String> otherIds = driveIds(text(row, cols, SheetField.OTHER_DOCUMENTS));

        String rowKey = sha256(String.join("|",
                tsRaw == null ? "" : FieldParsers.clean(tsRaw),
                fullName == null ? "" : fullName.toLowerCase(Locale.ROOT),
                email == null ? "" : email,
                mobile == null ? "" : mobile));

        return new ParsedRow(rowNumber, rowKey, appliedAt, applicationDate, fullName, dob, mobile, email,
                FieldParsers.truncate(text(row, cols, SheetField.ADDRESS), 1000),
                FieldParsers.truncate(text(row, cols, SheetField.QUALIFICATION), 200),
                FieldParsers.truncate(text(row, cols, SheetField.INSTITUTION), 200),
                passingYear,
                FieldParsers.truncate(text(row, cols, SheetField.PREVIOUS_COMPANY), 200),
                exp,
                FieldParsers.truncate(text(row, cols, SheetField.PREVIOUS_DESIGNATION), 150),
                FieldParsers.truncate(text(row, cols, SheetField.SKILLS), 2000),
                FieldParsers.truncate(text(row, cols, SheetField.EXPECTED_SALARY), 100),
                FieldParsers.truncate(text(row, cols, SheetField.DEPARTMENT), 150),
                FieldParsers.truncate(text(row, cols, SheetField.REFERENCE_NAME), 150),
                FieldParsers.truncate(text(row, cols, SheetField.REFERENCE_RELATION), 150),
                FieldParsers.truncate(text(row, cols, SheetField.REFERENCE_CONTACT), 200),
                FieldParsers.truncate(text(row, cols, SheetField.FAMILY_REF_NAME), 150),
                FieldParsers.truncate(text(row, cols, SheetField.FAMILY_REF_RELATION), 100),
                FieldParsers.truncate(text(row, cols, SheetField.FAMILY_REF_CONTACT), 200),
                resumeIds, aadhaarIds, certIds, otherIds, errors, warnings);
    }

    static Instant parseTimestamp(Object raw, ZoneId zone) {
        if (raw == null) return null;
        if (raw instanceof Number n) {
            double serial = n.doubleValue();
            if (serial < 1) return null;
            long days = (long) Math.floor(serial);
            long nanos = Math.round((serial - days) * 86_400_000L) * 1_000_000L;
            return LocalDate.of(1899, 12, 30).plusDays(days).atStartOfDay().plusNanos(nanos).atZone(zone).toInstant();
        }
        String s = FieldParsers.clean(raw);
        if (s == null) return null;
        for (DateTimeFormatter f : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(s, f).atZone(zone).toInstant();
            } catch (DateTimeParseException ignored) {
                // next
            }
        }
        LocalDate d = FieldParsers.parseDate(s);
        return d == null ? null : d.atStartOfDay(zone).toInstant();
    }

    static List<String> driveIds(String value) {
        if (value == null) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = DRIVE_ID.matcher(value);
        while (m.find()) ids.add(m.group(1));
        return List.copyOf(ids);
    }

    private static Object cell(List<Object> row, Map<SheetField, Integer> cols, SheetField f) {
        Integer idx = cols.get(f);
        if (idx == null || idx >= row.size()) return null;
        Object v = row.get(idx);
        return v instanceof String s && s.isBlank() ? null : v;
    }

    private static String text(List<Object> row, Map<SheetField, Integer> cols, SheetField f) {
        return FieldParsers.clean(cell(row, cols, f));
    }

    private static int indexOfNormalized(List<String> headers, String normalized) {
        if (normalized.isEmpty()) return -1;
        for (int i = 0; i < headers.size(); i++) if (SheetField.normalize(headers.get(i)).equals(normalized)) return i;
        return -1;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
