package com.nhantr.social_service.client;

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

    public List<UserSummary> search(String keyword) {
        return client.get()
                .uri(uri -> uri.path("/internal/v1/users/search").queryParam("keyword", keyword).build())
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<UserSummary> batch(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return client.post()
                .uri("/internal/v1/users/batch")
                .header("X-Internal-Api-Key", internalApiKey)
                .body(new BatchRequest(ids))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public boolean exists(Long userId) {
        return batch(List.of(userId)).stream().anyMatch(user -> user.id().equals(userId));
    }

    private record BatchRequest(List<Long> ids) {}

    public record UserSummary(
            Long id,
            String username,
            String email,
            String phoneNumber,
            String displayName,
            String avatarUrl,
            String bio,
            String status,
            String authProvider,
            String friendInviteCode,
            java.time.Instant createdAt
    ) {}
}
