package com.nhantr.post_service.client;

import com.nhantr.common.api.ApiException;
import com.nhantr.common.web.RequestTracing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Objects;

@Component
public class AiClient {
    private final RestClient client;
    private final String internalApiKey;
    public AiClient(@Value("${clients.ai.base-url:${AI_SERVICE_URL:http://localhost:8086}}") String baseUrl,
                    @Value("${app.internal-api-key}") String internalApiKey) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); factory.setReadTimeout(2000);
        client = RequestTracing.restClientBuilder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalApiKey = internalApiKey;
    }
    public void validate(Long userId, Long id, String mediaUrl) {
        Generation generation;
        try {
            generation = client.get().uri("/internal/v1/ai/generations/{id}?userId={userId}", id, userId)
                    .header("X-Internal-Api-Key", internalApiKey).retrieve().body(Generation.class);
        } catch (RuntimeException unavailable) {
            throw invalid();
        }
        if (generation == null || !Objects.equals(id, generation.id()) || !Objects.equals(userId, generation.userId())
                || !"SUCCESS".equals(generation.status()) || mediaUrl == null
                || !mediaUrl.equals(generation.generatedMediaUrl())) throw invalid();
    }
    private ApiException invalid() {
        return ApiException.badRequest("AI_REFERENCE_INVALID", "Use your successful AI generation and its exact generated media URL.");
    }
    public record Generation(Long id, Long userId, String status, String generatedMediaUrl) {}
}
