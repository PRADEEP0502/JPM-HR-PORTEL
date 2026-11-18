package com.jpm.hrms.communication;

import java.util.List;
import java.util.Map;

/**
 * Version-1 wording for every message purpose, created on first start. Admins can approve new versions in
 * Settings. No template invents details (addresses, dates, salaries): company name, venue and signature come from
 * Settings; anything not known is left to HR to confirm.
 */
final class DefaultTemplates {
    private DefaultTemplates() {
    }

    record Wording(String emailSubject, String body, String whatsapp) {
        Wording(String emailSubject, String body) {
            this(emailSubject, body, null);
        }

        /** WhatsApp shows *text* in bold; the email uses the plain heading. */
        String whatsappBody() {
            return whatsapp != null ? whatsapp : body.replaceAll("(?m)^(Interview Details|Reporting Details)$", "*$1*");
        }
    }

    static final Map<TemplatePurpose, Wording> ALL = Map.of(
            TemplatePurpose.APPLICATION_ACK, new Wording("Application Received – JPM", """
                    Dear {{CandidateName}},

                    Thank you for applying to {{CompanyName}} for the {{Department}} department.

                    We have received your application. Our HR team will review it and contact you if your profile matches our current requirements.

                    Best regards,
                    {{HRSignature}}"""),
            TemplatePurpose.INTERVIEW_INVITATION, new Wording(CommunicationService.DEFAULT_EMAIL_SUBJECT, CommunicationService.DEFAULT_EMAIL_BODY,
                    CommunicationService.DEFAULT_WHATSAPP_BODY),
            TemplatePurpose.INTERVIEW_REMINDER, new Wording("Interview Reminder – JPM", """
                    Dear {{CandidateName}},

                    This is a reminder of your interview with {{CompanyName}} for the {{Department}} department.

                    Interview Details

                    Date: {{InterviewDate}}
                    Time: {{InterviewTime}}
                    Venue: {{Venue}}

                    If you are unable to attend, please let us know as soon as possible.

                    Best regards,
                    {{HRSignature}}"""),
            TemplatePurpose.SELECTION, new Wording("Selection Update – JPM", """
                    Dear {{CandidateName}},

                    Congratulations! We are pleased to inform you that you have been selected for the {{Department}} department at {{CompanyName}}.

                    Our HR team will contact you shortly with the next steps.

                    Best regards,
                    {{HRSignature}}"""),
            TemplatePurpose.REJECTION, new Wording("Application Update – JPM", """
                    Dear {{CandidateName}},

                    Thank you for your interest in {{CompanyName}} and for the time you gave to our recruitment process for the {{Department}} department.

                    After careful consideration, we will not be moving forward with your application at this time.

                    We wish you every success in your career.

                    Best regards,
                    {{HRSignature}}"""),
            TemplatePurpose.JOINING, new Wording("Welcome to JPM – Joining Instructions", """
                    Dear {{CandidateName}},

                    Congratulations, and welcome to {{CompanyName}}! We are delighted to have you join our {{Department}} team.

                    Reporting Details

                    Date of joining: {{JoiningDate}}
                    Department: {{Department}}
                    Reporting location: {{Venue}}

                    Kindly report to the HR department on your date of joining. Our HR team will share your reporting time and the list of documents to bring before that day.

                    Should you have any questions in the meantime, please reply to this message and we will be glad to assist you.

                    We look forward to working with you.

                    Warm regards,
                    {{HRSignature}}"""));

    /** The original joining email subject; replaced by the one above when the wording is upgraded. */
    static final String PREVIOUS_JOINING_SUBJECT = "Joining Instructions – JPM";

    /** Earlier default joining wordings; templates still on one of them are moved to the wording above. */
    static final List<String> PREVIOUS_JOINING_BODIES = List.of("""
            Dear {{CandidateName}},

            Welcome to {{CompanyName}}!

            Reporting Details

            Department: {{Department}}
            Reporting location: {{Venue}}

            Our HR team will confirm your joining date, reporting time and the documents to bring. Please reply to this message if you have any questions.

            Best regards,
            {{HRSignature}}""", """
            Dear {{CandidateName}},

            Welcome to {{CompanyName}}!

            Reporting Details

            Department: {{Department}}
            Joining date: {{JoiningDate}}
            Reporting location: {{Venue}}

            Please report on your joining date. Our HR team will confirm the reporting time and the documents to bring. Please reply to this message if you have any questions.

            Best regards,
            {{HRSignature}}""");
}
