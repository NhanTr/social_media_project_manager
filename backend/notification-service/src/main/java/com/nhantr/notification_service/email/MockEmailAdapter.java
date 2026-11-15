package com.nhantr.notification_service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class MockEmailAdapter {
    private static final Logger log = LoggerFactory.getLogger(MockEmailAdapter.class);
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile("(?i)(.*(token|otp|secret|password|code|key).*)");

    private final EmailTemplateService templateService;

    public MockEmailAdapter(EmailTemplateService templateService) {
        this.templateService = templateService;
    }

    public EmailDeliveryResult send(EmailSendRequest request) {
        templateService.render(request.templateType(), request.variables());

        String messageId = "email-mock-" + UUID.randomUUID();
        log.info("event=mock_email_delivery status=success message_id={} template={}", messageId, request.templateType());

        return EmailDeliveryResult.delivered(messageId);
    }

    public String sanitizeVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return "{}";
        }
        Map<String, String> sanitized = variables.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            String k = e.getKey();
                            if (SENSITIVE_KEY_PATTERN.matcher(k).matches()) {
                                return "[REDACTED]";
                            }
                            return String.valueOf(e.getValue());
                        }
                ));
        return sanitized.toString();
    }
}

