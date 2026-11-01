package com.nhantr.notification_service.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record EmailSendRequest(
        @NotBlank @Email String to,
        @NotNull EmailTemplateType templateType,
        Map<String, Object> variables,
        String idempotencyKey
) {}

