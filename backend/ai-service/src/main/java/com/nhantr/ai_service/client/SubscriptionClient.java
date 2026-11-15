package com.nhantr.ai_service.client;

import com.nhantr.common.api.ApiException;
import com.nhantr.common.web.RequestTracing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Instant;

@Component
public class SubscriptionClient {
    private final RestClient client;
    private final String internalApiKey;

    public SubscriptionClient(@Value("${clients.subscription.base-url}") String baseUrl,
                              @Value("${app.internal-api-key}") String internalApiKey) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.client = RequestTracing.restClientBuilder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalApiKey = internalApiKey;
    }

    public void requireAi(Long userId) {
        Entitlement entitlement;
        try {
            entitlement = client.get().uri("/internal/v1/subscriptions/{id}/entitlements", userId)
                    .header("X-Internal-Api-Key", internalApiKey).retrieve().body(Entitlement.class);
        } catch (RuntimeException unavailable) {
            throw denied();
        }
        if (entitlement == null || !Boolean.TRUE.equals(entitlement.aiGeneration())
                || entitlement.validUntil() == null || !entitlement.validUntil().isAfter(Instant.now())) {
            throw denied();
        }
    }
    private ApiException denied() {
        return ApiException.forbidden("AI_TIER_REQUIRED", "An active AI subscription is required.");
    }
    public record Entitlement(Boolean aiGeneration, Instant validUntil) {}
}
