package com.nhantr.social_service.client;

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

    public void friendRequest(Long receiverId, Long senderId, Long requestId) {
        send("/internal/v1/notifications/friend-request", receiverId, senderId, requestId);
    }

    public void friendAccepted(Long receiverId, Long actorId, Long friendshipId) {
        send("/internal/v1/notifications/friend-accept", receiverId, actorId, friendshipId);
    }

    private void send(String path, Long userId, Long actorId, Long referenceId) {
        try {
            client.post().uri(path)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .header("Idempotency-Key", UUID.nameUUIDFromBytes((path + ":" + referenceId).getBytes()).toString())
                    .body(new NotificationEvent(userId, actorId, referenceId, null, null))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            log.warn("Notification service unavailable for event {}: {}", path, exception.getMessage());
        }
    }

    public record NotificationEvent(Long userId, Long actorUserId, Long referenceId, String title, String body) {}
}
