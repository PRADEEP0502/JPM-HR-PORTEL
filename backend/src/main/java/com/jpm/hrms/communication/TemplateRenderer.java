package com.jpm.hrms.communication;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {{Placeholder}} tokens. Rendering fails (instead of producing a half-filled message) when
 * any placeholder has no value, so no message can ever leave with an unresolved placeholder.
 */
public final class TemplateRenderer {
    private TemplateRenderer() {
    }

    public static final String CANDIDATE_NAME = "CandidateName";
    public static final String DEPARTMENT = "Department";
    public static final String INTERVIEW_DATE = "InterviewDate";
    public static final String INTERVIEW_TIME = "InterviewTime";
    public static final String VENUE = "Venue";
    public static final String COMPANY_NAME = "CompanyName";
    public static final String HR_SIGNATURE = "HRSignature";
    public static final String INTERVIEW_ROUND = "InterviewRound";

    /** Placeholders a template may use, with a human description shown in the admin UI. */
    public static final Map<String, String> PLACEHOLDERS = new LinkedHashMap<>();

    static {
        PLACEHOLDERS.put(CANDIDATE_NAME, "Candidate's full name");
        PLACEHOLDERS.put(DEPARTMENT, "Department applied for");
        PLACEHOLDERS.put(INTERVIEW_DATE, "Interview date, e.g. Monday, 27 October 2026");
        PLACEHOLDERS.put(INTERVIEW_TIME, "Interview time, e.g. 11:30 AM");
        PLACEHOLDERS.put(VENUE, "Configured interview venue");
        PLACEHOLDERS.put(COMPANY_NAME, "Configured company name");
        PLACEHOLDERS.put(HR_SIGNATURE, "Configured HR signature");
        PLACEHOLDERS.put(INTERVIEW_ROUND, "Interview round, e.g. HR Round");
    }

    /** Placeholders that only exist when the message is about a specific interview. */
    public static final List<String> INTERVIEW_PLACEHOLDERS = List.of(INTERVIEW_DATE, INTERVIEW_TIME, INTERVIEW_ROUND);

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z]+)\\s*}}");

    public static final class MissingValuesException extends RuntimeException {
        private final List<String> missing;

        public MissingValuesException(List<String> missing) {
            super("Missing details: " + String.join(", ", missing));
            this.missing = missing;
        }

        public List<String> getMissing() {
            return missing;
        }
    }

    public static Set<String> placeholdersIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null) return found;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    /** Returns problems with a template draft (unknown or malformed placeholders, missing required ones). */
    public static List<String> validate(TemplatePurpose purpose, String subject, String body) {
        List<String> problems = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>(placeholdersIn(body));
        used.addAll(placeholdersIn(subject));
        for (String p : used) {
            if (!PLACEHOLDERS.containsKey(p)) problems.add("Unknown placeholder {{" + p + "}}");
            else if (!purpose.allows(p)) problems.add("{{" + p + "}} is only available in interview invitations and reminders");
        }
        for (String r : purpose.required()) {
            if (!placeholdersIn(body).contains(r)) problems.add("The message must contain {{" + r + "}}");
        }
        String stripped = TOKEN.matcher((subject == null ? "" : subject) + "\n" + body).replaceAll("");
        if (stripped.contains("{{") || stripped.contains("}}")) problems.add("Malformed placeholder - use the form {{Name}}");
        return problems;
    }

    public static String render(String template, Map<String, String> values) {
        if (template == null) return null;
        List<String> missing = new ArrayList<>();
        Matcher m = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            if (v == null || v.isBlank()) {
                if (!missing.contains(m.group(1))) missing.add(m.group(1));
                v = "";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(v));
        }
        m.appendTail(out);
        if (!missing.isEmpty()) throw new MissingValuesException(missing);
        String result = out.toString();
        if (result.contains("{{") || result.contains("}}")) throw new MissingValuesException(List.of("unresolved placeholder"));
        return result;
    }
}
