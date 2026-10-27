package com.jpm.hrms.communication;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunicationUnitTest {

    @Test
    void validatesAndNormalisesPhoneNumbers() {
        assertThat(ContactValidator.checkPhone("9876543210", "91").international()).isEqualTo("919876543210");
        assertThat(ContactValidator.checkPhone("+91 98765-43210", "91").international()).isEqualTo("919876543210");
        assertThat(ContactValidator.checkPhone("09876543210", "91").international()).isEqualTo("919876543210");
        assertThat(ContactValidator.checkPhone("+44 7911 123456", "91").international()).isEqualTo("447911123456");
        assertThat(ContactValidator.checkPhone("0091 9876543210", "91").international()).isEqualTo("919876543210");
        // invalid
        assertThat(ContactValidator.checkPhone("12345", "91").valid()).isFalse();
        assertThat(ContactValidator.checkPhone("1234567890", "91").valid()).isFalse(); // Indian mobiles start 6-9
        assertThat(ContactValidator.checkPhone("+91 98765", "91").valid()).isFalse();
        assertThat(ContactValidator.checkPhone(null, "91").reason()).isEqualTo("No mobile number on file");
        assertThat(ContactValidator.checkPhone("+1234567890123456", "91").valid()).isFalse(); // > 15 digits
    }

    @Test
    void validatesEmail() {
        assertThat(ContactValidator.checkEmail("a.b@example.com").valid()).isTrue();
        assertThat(ContactValidator.checkEmail("not-an-email").valid()).isFalse();
        assertThat(ContactValidator.checkEmail(" ").reason()).isEqualTo("No email address on file");
    }

    @Test
    void whatsAppUrlIsEncodedCorrectly() {
        String msg = "Dear A & B,\n\n*Interview Details*\nVenue: JPM – Junior Processing Mill?#100%";
        String url = ContactValidator.whatsAppUrl("919876543210", msg);
        assertThat(url).startsWith("https://wa.me/919876543210?text=");
        String encoded = url.substring(url.indexOf("text=") + 5);
        assertThat(encoded).doesNotContain("+").doesNotContain(" ").doesNotContain("\n").doesNotContain("&").doesNotContain("#");
        // "*" (WhatsApp bold) is a URL-safe character and is left as-is, like encodeURIComponent does
        assertThat(encoded).contains("%20").contains("%0A").contains("%26").contains("%E2%80%93").contains("%23").contains("%25");
        assertThat(URLDecoder.decode(encoded, StandardCharsets.UTF_8)).isEqualTo(msg);
    }

    @Test
    void rendersAndRefusesUnresolvedPlaceholders() {
        assertThat(TemplateRenderer.render("Hi {{CandidateName}}, {{ Department }}", Map.of("CandidateName", "Asha", "Department", "HR")))
                .isEqualTo("Hi Asha, HR");
        assertThatThrownBy(() -> TemplateRenderer.render("Hi {{CandidateName}} {{Department}}", Map.of("CandidateName", "Asha")))
                .isInstanceOf(TemplateRenderer.MissingValuesException.class)
                .hasMessageContaining("Department");
        // values containing $ or \ must be inserted literally
        assertThat(TemplateRenderer.render("{{CandidateName}}", Map.of("CandidateName", "R$ \\ Kumar"))).isEqualTo("R$ \\ Kumar");
    }

    @Test
    void validatesTemplateDrafts() {
        assertThat(TemplateRenderer.validate(TemplatePurpose.INTERVIEW_INVITATION, null, "Dear {{CandidateName}} on {{InterviewDate}} at {{InterviewTime}}")).isEmpty();
        assertThat(TemplateRenderer.validate(TemplatePurpose.INTERVIEW_INVITATION, null, "Dear {{CandidateName}} {{Salary}} {{InterviewDate}} {{InterviewTime}}"))
                .anyMatch(p -> p.contains("Unknown placeholder {{Salary}}"));
        assertThat(TemplateRenderer.validate(TemplatePurpose.INTERVIEW_INVITATION, null, "Dear {{CandidateName}}")).anyMatch(p -> p.contains("{{InterviewDate}}"));
        assertThat(TemplateRenderer.validate(TemplatePurpose.INTERVIEW_INVITATION, null, "Dear {{CandidateName} {{InterviewDate}} {{InterviewTime}}"))
                .anyMatch(p -> p.contains("Malformed"));
    }

    @Test
    void defaultTemplatesFollowTheApprovedWording() {
        assertThat(CommunicationService.DEFAULT_EMAIL_SUBJECT).isEqualTo("Interview Invitation – JPM");
        assertThat(TemplateRenderer.validate(TemplatePurpose.INTERVIEW_INVITATION, CommunicationService.DEFAULT_EMAIL_SUBJECT, CommunicationService.DEFAULT_EMAIL_BODY)).isEmpty();
        assertThat(TemplateRenderer.validate(TemplatePurpose.INTERVIEW_INVITATION, null, CommunicationService.DEFAULT_WHATSAPP_BODY)).isEmpty();
        assertThat(CommunicationService.DEFAULT_WHATSAPP_BODY).contains("\n*Interview Details*\n");
        assertThat(CommunicationService.DEFAULT_EMAIL_BODY).contains("\nInterview Details\n").doesNotContain("*");
    }
}
