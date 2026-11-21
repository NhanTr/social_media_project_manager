package com.nhantr.identity_service.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAuditLoggerTests {
    private final Logger logger = (Logger) LoggerFactory.getLogger(AuthAuditLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void cleanUp() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void recordsOnlyStableRedactedFields() {
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "request-123");

        new AuthAuditLogger().record("otp_verification", "succeeded", 42L);

        String message = appender.list.getFirst().getFormattedMessage();
        assertThat(message)
                .contains("event=otp_verification")
                .contains("outcome=succeeded")
                .contains("userId=42")
                .contains("requestId=request-123")
                .doesNotContain("654321")
                .doesNotContain("refresh-secret")
                .doesNotContain("+84900000001")
                .doesNotContain("person@example.com")
                .doesNotContain("google-payload");
    }
}
