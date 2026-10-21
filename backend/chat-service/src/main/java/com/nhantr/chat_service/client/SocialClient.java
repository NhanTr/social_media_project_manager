package com.nhantr.chat_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    public boolean friends(Long a, Long b) {
        return check("/internal/v1/friends/check", a, b);
    }

    public boolean blocked(Long a, Long b) {
        return check("/internal/v1/blocks/check", a, b);
    }

    private boolean check(String path, Long a, Long b) {
        CheckResponse response = client.get()
                .uri(uri -> uri.path(path).queryParam("userA", a).queryParam("userB", b).build())
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve().body(CheckResponse.class);
        return response != null && response.value();
    }

    private record CheckResponse(boolean value) {}
}
