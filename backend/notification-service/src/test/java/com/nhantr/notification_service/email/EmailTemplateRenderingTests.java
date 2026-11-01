package com.nhantr.notification_service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRenderingTests {

    private EmailTemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new EmailTemplateService();
    }

    @Test
    void renderAuthWelcomeTemplate() {
        RenderedEmail email = templateService.render(
                EmailTemplateType.AUTH_WELCOME,
                Map.of("username", "Tuấn Dũng")
        );

        assertThat(email.subject()).contains("Welcome to Locket!");
        assertThat(email.textBody()).contains("Hi Tuấn Dũng");
        assertThat(email.htmlBody()).contains("home screens");
    }

    @Test
    void renderAuthOtpAlertTemplate() {
        RenderedEmail email = templateService.render(
                EmailTemplateType.AUTH_OTP_ALERT,
                Map.of("username", "Tuấn Dũng", "otp", "849201")
        );

        assertThat(email.subject()).contains("Verification Code");
        assertThat(email.textBody()).contains("849201");
        assertThat(email.htmlBody()).contains("<b>849201</b>");
    }

    @Test
    void renderSubscriptionActivatedTemplate() {
        RenderedEmail email = templateService.render(
                EmailTemplateType.SUBSCRIPTION_ACTIVATED,
                Map.of("username", "Tuấn Dũng", "plan", "Locket Gold")
        );

        assertThat(email.subject()).contains("Locket Gold Activated!");
        assertThat(email.textBody()).contains("unlimited friends");
        assertThat(email.htmlBody()).contains("Locket Gold");
    }

    @Test
    void renderSubscriptionCancelledTemplate() {
        RenderedEmail email = templateService.render(
                EmailTemplateType.SUBSCRIPTION_CANCELLED,
                Map.of("username", "Tuấn Dũng")
        );

        assertThat(email.subject()).contains("Subscription Cancelled");
        assertThat(email.textBody()).contains("end of your billing cycle");
    }
}

