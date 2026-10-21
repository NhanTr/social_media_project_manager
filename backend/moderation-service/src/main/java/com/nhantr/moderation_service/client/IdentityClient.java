package com.nhantr.moderation_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class IdentityClient {
    private final RestClient client;
    private final String internalApiKey;

    public IdentityClient(
            @Value("${clients.identity.base-url}") String baseUrl,
            @Value("${app.internal-api-key}") String internalApiKey
    ) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public boolean exists(Long userId) {
        List<UserSummary> users = client.post().uri("/internal/v1/users/batch")
                .header("X-Internal-Api-Key", internalApiKey)
                .body(new BatchRequest(List.of(userId)))
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return users != null && users.stream().anyMatch(user -> user.id().equals(userId));
    }

    public void ban(Long userId) {
        client.post().uri("/internal/v1/users/{id}/status", userId)
                .header("X-Internal-Api-Key", internalApiKey)
                .body(new StatusRequest("BANNED"))
                .retrieve().toBodilessEntity();
    }

    private record BatchRequest(List<Long> ids) {}
    private record StatusRequest(String status) {}
    private record UserSummary(Long id) {}
}
