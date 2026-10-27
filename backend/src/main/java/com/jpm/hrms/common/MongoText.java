package com.jpm.hrms.common;

/** Builds MongoDB regular expressions from user-entered text, with every regex metacharacter escaped. */
public final class MongoText {
    private static final String SPECIAL = "\\^$.|?*+()[]{}/-";

    private MongoText() {
    }

    public static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (char ch : text.toCharArray()) {
            if (SPECIAL.indexOf(ch) >= 0) sb.append('\\');
            sb.append(ch);
        }
        return sb.toString();
    }

    /** Case-insensitive "contains" pattern; use with {@code Criteria.regex(pattern, "i")}. */
    public static String contains(String text) {
        return escape(text.trim());
    }

    /** Whole-value pattern; use with {@code Criteria.regex(pattern, "i")} for case-insensitive equality. */
    public static String exact(String text) {
        return "^" + escape(text) + "$";
    }
}
