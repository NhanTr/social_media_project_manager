package com.nhantr.moderation_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PostClient {
    private final RestClient client;
    private final String internalApiKey;

    public PostClient(
            @Value("${clients.post.base-url}") String baseUrl,
            @Value("${app.internal-api-key}") String internalApiKey
    ) {
        var transport = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(java.time.Duration.ofSeconds(2));
        transport.setReadTimeout(java.time.Duration.ofSeconds(5));
        this.client = com.nhantr.common.web.RequestTracing.restClientBuilder()
                .requestFactory(transport).baseUrl(baseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public PostSummary get(Long postId) {
        return client.get().uri("/internal/v1/posts/{id}", postId)
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve().body(PostSummary.class);
    }

    public void status(Long postId, String status) {
        client.post().uri("/internal/v1/posts/{id}/status", postId)
                .header("X-Internal-Api-Key", internalApiKey)
                .body(new StatusRequest(status))
                .retrieve().toBodilessEntity();
    }

    private record StatusRequest(String status) {}
    public record PostSummary(Long id, Long userId, String status) {}
}
