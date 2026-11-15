package com.nhantr.moderation_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class NotificationClient {
    private final RestClient client;
    private final String internalApiKey;

    public NotificationClient(
            @Value("${clients.notification.base-url}") String baseUrl,
            @Value("${app.internal-api-key}") String internalApiKey
    ) {
        var transport = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(java.time.Duration.ofSeconds(2));
        transport.setReadTimeout(java.time.Duration.ofSeconds(5));
        this.client = com.nhantr.common.web.RequestTracing.restClientBuilder()
                .requestFactory(transport).baseUrl(baseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public void reportResult(Long reporterId, Long reportId, String body) {
        deliver(reporterId, reportId, "report", body);
    }

    public void warning(Long userId, Long reportId, String note) {
        deliver(userId, reportId, "warning", "Moderation warning: " + note);
    }

    private void deliver(Long userId, Long reportId, String kind, String body) {
        client.post().uri("/internal/v1/notifications/report-result")
                .header("X-Internal-Api-Key", internalApiKey)
                .header("Idempotency-Key", UUID.nameUUIDFromBytes((kind + ":" + reportId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString())
                .body(new Event(userId, null, reportId, null, body))
                .retrieve().toBodilessEntity();
    }

    private record Event(Long userId, Long actorUserId, Long referenceId, String title, String body) {}
}
