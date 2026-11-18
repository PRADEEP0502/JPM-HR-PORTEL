package com.jpm.hrms.sync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tidies an address where the candidate typed the whole thing into "Door No., Street and Area"
 * (e.g. "3/2120 Vel Nagar, Kalipalayam, Tirupur 641666"): the PIN code and a trailing town / district / state that
 * are (or become) separate fields are taken out of the street line. Nothing is ever guessed when the address does
 * not end with a PIN code, and the street line is never left empty.
 */
public final class AddressParts {
    private AddressParts() {
    }

    public record Parts(String address, String city, String district, String state, String pincode) {
    }

    /** A 6-digit Indian PIN code ("641666" or "641 666"), not part of a longer number such as a phone number. */
    private static final Pattern PIN = Pattern.compile("(?<![0-9])([1-9][0-9]{2})\\s?([0-9]{3})(?![0-9])");
    private static final Pattern SEPARATORS = Pattern.compile("\\s*(?:,|\\n|;)\\s*");
    private static final List<String> COUNTRY = List.of("india", "ind");
    private static final List<String> TAMIL_NADU = List.of("tamilnadu", "tn");

    public static Parts tidy(String address, String city, String district, String state, String pincode) {
        if (address == null || address.isBlank()) return new Parts(address, city, district, state, pincode);
        String a = address.trim();

        // 1. the PIN code typed into the street line
        Matcher m = PIN.matcher(a);
        int start = -1, end = -1;
        String pinInAddress = null;
        while (m.find()) {
            start = m.start();
            end = m.end();
            pinInAddress = m.group(1) + m.group(2);
        }
        boolean pinAtEnd = pinInAddress != null && onlyPlacesAfter(a.substring(end), city, district, state);
        if (!pinAtEnd) return new Parts(a, city, district, state, pincode); // a full address always ends with the PIN
        if (pincode == null) pincode = pinInAddress;
        if (!pincode.equals(pinInAddress)) return new Parts(a, city, district, state, pincode); // two different PINs: leave it to HR
        String tail = a.substring(end);
        a = (a.substring(0, start) + " " + tail).trim();

        // 2. trailing parts that repeat the town / district / state, or just say "India"
        List<String> parts = new ArrayList<>(Arrays.stream(SEPARATORS.split(a)).map(String::trim).filter(s -> !s.isEmpty()).toList());
        boolean changed = true;
        while (changed && parts.size() > 1) {
            changed = false;
            String last = parts.get(parts.size() - 1);
            String key = key(last);
            if (key.isEmpty() || COUNTRY.contains(key)) {
                changed = true;
            } else if (TAMIL_NADU.contains(key) && (state == null || TAMIL_NADU.contains(key(state)))) {
                if (state == null) state = "Tamil Nadu";
                changed = true;
            } else if (same(key, state) || same(key, district) || same(key, city)) {
                changed = true;
            } else if (city == null && looksLikePlace(last)) {
                // the PIN came right after it, so the last part is the town
                city = titleCase(last);
                changed = true;
            }
            if (changed) parts.remove(parts.size() - 1);
        }
        String street = String.join(", ", parts).replaceAll("[\\s,.-]+$", "").trim();
        // nothing but the town left (e.g. "Tirupur 641666"): keep what the candidate wrote
        boolean onlyTown = parts.size() == 1 && (same(key(street), city) || same(key(street), district) || same(key(street), state));
        return new Parts(street.isEmpty() || onlyTown ? address.trim() : street, city, district, state, pincode);
    }

    /** The PIN ends the address: nothing follows it, or only ", Tamil Nadu", ", India" or the town / district / state. */
    private static boolean onlyPlacesAfter(String rest, String city, String district, String state) {
        String r = rest.trim();
        if (r.isEmpty()) return true;
        if (!r.matches("(?s)^[,;.\\-].*")) return false;
        for (String part : SEPARATORS.split(r.replaceFirst("^[,;.\\s\\-]+", ""))) {
            String k = key(part);
            boolean place = k.isEmpty() || COUNTRY.contains(k) || TAMIL_NADU.contains(k) || same(k, city) || same(k, district) || same(k, state);
            if (!place) return false;
        }
        return true;
    }

    private static boolean looksLikePlace(String s) {
        String t = s.trim();
        return t.matches("[\\p{L} .'-]+") && t.split("\\s+").length <= 3;
    }

    /** "Tiruppur Dt." and "tirupur" compare equal: letters only, repeated letters collapsed, "district"/"dt" dropped. */
    private static String key(String s) {
        if (s == null) return "";
        String k = s.toLowerCase(Locale.ROOT).replaceAll("\\b(district|dist|dt)\\b", "").replaceAll("[^a-z]", "");
        return k.replaceAll("(.)\\1+", "$1");
    }

    private static boolean same(String key, String field) {
        return field != null && !key.isEmpty() && key.equals(key(field));
    }

    private static String titleCase(String s) {
        StringBuilder out = new StringBuilder();
        for (String w : s.trim().split("\\s+")) {
            if (out.length() > 0) out.append(' ');
            out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
