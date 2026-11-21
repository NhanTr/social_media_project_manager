package com.nhantr.moderation_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class NotificationClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private final RestClient client;
    private final String internalApiKey;

    public NotificationClient(
            @Value("${clients.notification.base-url}") String baseUrl,
            @Value("${app.internal-api-key}") String internalApiKey
    ) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public void reportResult(Long reporterId, Long reportId, String body) {
        try {
            client.post().uri("/internal/v1/notifications/report-result")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .header("Idempotency-Key", UUID.nameUUIDFromBytes(("report:" + reportId).getBytes()).toString())
                    .body(new Event(reporterId, null, reportId, null, body))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            log.warn("Cannot create REPORT_RESULT notification: {}", exception.getMessage());
        }
    }

    private record Event(Long userId, Long actorUserId, Long referenceId, String title, String body) {}
}
