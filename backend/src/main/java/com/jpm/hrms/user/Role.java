package com.jpm.hrms.user;

/**
 * Internal dashboard roles. Candidates never log in: they apply through the Google Form only.
 * The stored names ADMIN and HR are kept for existing accounts; they are shown as HR Admin and HR Executive.
 */
public enum Role {
    /** HR Admin: everything, including users and roles, settings, templates, sync and the audit log. */
    ADMIN("HR Admin"),
    /** HR Executive: candidates, statuses, ratings, interviews, communication and reports. */
    HR("HR Executive"),
    /** Interviewer: only their own assigned interviews, a limited candidate summary, and interview feedback. */
    INTERVIEWER("Interviewer");

    public final String label;

    Role(String label) {
        this.label = label;
    }
}
