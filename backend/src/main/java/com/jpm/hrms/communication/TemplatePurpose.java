package com.jpm.hrms.communication;

import java.util.List;

/**
 * What a message template is for. Each purpose has its own approved WhatsApp and email wording.
 * Interview purposes need an upcoming interview and may use the interview placeholders; the others may not.
 */
public enum TemplatePurpose {
    APPLICATION_ACK("Application acknowledgement", false),
    INTERVIEW_INVITATION("Interview invitation", true),
    INTERVIEW_REMINDER("Interview reminder", true),
    SELECTION("Selection notification", false),
    REJECTION("Rejection notification", false),
    JOINING("Joining instructions", false);

    public final String label;
    public final boolean needsInterview;

    TemplatePurpose(String label, boolean needsInterview) {
        this.label = label;
        this.needsInterview = needsInterview;
    }

    /** Placeholders every template of this purpose must contain. */
    public List<String> required() {
        return needsInterview
                ? List.of(TemplateRenderer.CANDIDATE_NAME, TemplateRenderer.INTERVIEW_DATE, TemplateRenderer.INTERVIEW_TIME)
                : List.of(TemplateRenderer.CANDIDATE_NAME);
    }

    /** Placeholders this purpose may use (interview details only exist for interview messages). */
    public boolean allows(String placeholder) {
        return needsInterview || !TemplateRenderer.INTERVIEW_PLACEHOLDERS.contains(placeholder);
    }
}
