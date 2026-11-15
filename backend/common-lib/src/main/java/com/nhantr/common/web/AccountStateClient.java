package com.nhantr.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Shared bounded REST account-state check; callers decide protocol-specific deny response. */
@Component
public class AccountStateClient {
    private final RestClient client;
    public AccountStateClient(@Value("${clients.identity.base-url:${IDENTITY_SERVICE_URL:http://localhost:8081}}") String url,
                              @Value("${app.internal-api-key:}") String key) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000); factory.setReadTimeout(2000);
        client = RequestTracing.restClientBuilder().baseUrl(url).requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", key).build();
    }
    public boolean isActive(long id) {
        var state = client.get().uri("/internal/v1/users/{id}/active", id).retrieve().body(State.class);
        return state != null && state.active();
    }
    public record State(boolean active) {}
}
