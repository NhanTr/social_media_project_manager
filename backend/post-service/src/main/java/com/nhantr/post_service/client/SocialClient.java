package com.nhantr.post_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class SocialClient {
    private final RestClient client;
    private final String internalApiKey;

    public SocialClient(
            @Value("${clients.social.base-url}") String baseUrl,
            @Value("${app.internal-api-key}") String internalApiKey
    ) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public List<Long> friendIds(Long userId) {
        return client.get().uri("/internal/v1/friends/{id}/ids", userId)
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public boolean areFriends(Long a, Long b) {
        CheckResponse response = client.get()
                .uri(uri -> uri.path("/internal/v1/friends/check").queryParam("userA", a).queryParam("userB", b).build())
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve().body(CheckResponse.class);
        return response != null && response.value();
    }

    public boolean blocked(Long a, Long b) {
        CheckResponse response = client.get()
                .uri(uri -> uri.path("/internal/v1/blocks/check").queryParam("userA", a).queryParam("userB", b).build())
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve().body(CheckResponse.class);
        return response != null && response.value();
    }

    private record CheckResponse(boolean value) {}
}
