package com.nhantr.post_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiClient {
    private final RestClient client;
    private final String internalApiKey;

    public AiClient(
            @Value("${clients.ai.base-url:http://localhost:8086}") String baseUrl,
            @Value("${app.internal-api-key}") String internalApiKey
    ) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public VerifyResponse verify(Long userId, Long aiGenerationId) {
        return client.get()
                .uri(uri -> uri.path("/internal/v1/ai/generations/{id}/verify")
                        .queryParam("userId", userId)
                        .build(aiGenerationId))
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve()
                .body(VerifyResponse.class);
    }

    public record VerifyResponse(
            boolean valid,
            String generatedMediaUrl,
            String widgetThumbnailUrl,
            String originalMediaUrl,
            String errorMessage
    ) {}
}
