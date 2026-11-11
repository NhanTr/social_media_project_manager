package com.nhantr.notification_service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSecurityLoggingTests {

    private MockEmailAdapter emailAdapter;

    @BeforeEach
    void setUp() {
        emailAdapter = new MockEmailAdapter(new EmailTemplateService());
    }

    @Test
    void sanitizeVariablesMasksOtpCodeAndSecretTokens() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("username", "Tuấn Dũng");
        vars.put("otp", "849201");
        vars.put("token", "secret-bearer-jwt-token-xyz");
        vars.put("password", "SuperSecretPassword123!");

        String sanitized = emailAdapter.sanitizeVariables(vars);

        // Sensitive keys must be redacted
        assertThat(sanitized).doesNotContain("849201");
        assertThat(sanitized).doesNotContain("secret-bearer-jwt-token-xyz");
        assertThat(sanitized).doesNotContain("SuperSecretPassword123!");

        // Must contain REDACTED
        assertThat(sanitized).contains("[REDACTED]");
        // Non-sensitive values remain intact
        assertThat(sanitized).contains("Tuấn Dũng");
    }

    @Test
    void sendEmailProcessesSuccessfullyWithoutExposingRawTokensInSanitizedState() {
        Map<String, Object> vars = Map.of(
                "username", "Tuấn Dũng",
                "otpCode", "991122",
                "sessionToken", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        );

        EmailSendRequest request = new EmailSendRequest(
                "tuandung@example.com",
                EmailTemplateType.AUTH_OTP_ALERT,
                vars,
                "email-key-1"
        );

        EmailDeliveryResult result = emailAdapter.send(request);

        assertThat(result.success()).isTrue();
        assertThat(result.messageId()).isNotNull();

        String sanitized = emailAdapter.sanitizeVariables(vars);
        assertThat(sanitized).doesNotContain("991122");
        assertThat(sanitized).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
    }
}

