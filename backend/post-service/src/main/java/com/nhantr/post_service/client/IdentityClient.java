package com.nhantr.post_service.client;

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

    public List<UserSummary> batch(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return client.post().uri("/internal/v1/users/batch")
                .header("X-Internal-Api-Key", internalApiKey)
                .body(new BatchRequest(ids))
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    private record BatchRequest(List<Long> ids) {}
    public record UserSummary(Long id, String username, String displayName, String avatarUrl) {}
}
