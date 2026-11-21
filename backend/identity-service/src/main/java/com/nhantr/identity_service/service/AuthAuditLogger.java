package com.nhantr.identity_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class AuthAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(AuthAuditLogger.class);

    public void record(String event, String outcome, Long userId) {
        try {
            log.info(
                    "auth_audit event={} outcome={} userId={} requestId={}",
                    event,
                    outcome,
                    userId,
                    MDC.get("requestId")
            );
        } catch (RuntimeException ignored) {
            // Authentication behavior must never depend on audit transport health.
        }
    }
}
