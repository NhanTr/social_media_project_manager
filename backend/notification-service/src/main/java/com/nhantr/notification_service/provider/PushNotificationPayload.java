package com.nhantr.notification_service.provider;

import java.util.Map;

public record PushNotificationPayload(
        String token,
        String title,
        String body,
        String type,
        Long referenceId,
        Map<String, String> data
) {
    public static PushNotificationPayload of(
            String token,
            String title,
            String body,
            String type,
            Long referenceId
    ) {
        return new PushNotificationPayload(
                token,
                title,
                body,
                type,
                referenceId,
                Map.of(
                        "type", type != null ? type : "",
                        "referenceId", referenceId != null ? referenceId.toString() : ""
                )
        );
    }
}

