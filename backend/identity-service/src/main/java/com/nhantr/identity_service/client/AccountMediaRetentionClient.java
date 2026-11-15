package com.nhantr.identity_service.client;

import com.nhantr.common.web.RequestTracing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AccountMediaRetentionClient {
    private final RestClient client;
    public AccountMediaRetentionClient(@Value("${clients.post.base-url:${POST_SERVICE_URL:http://localhost:8083}}") String url,
                                      @Value("${app.internal-api-key}") String key) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); factory.setReadTimeout(5000);
        client = RequestTracing.restClientBuilder().baseUrl(url).requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", key).build();
    }
    public boolean cleanup(Long userId) {
        Result result = client.post().uri("/internal/v1/retention/accounts/{id}?dryRun=false", userId)
                .retrieve().body(Result.class);
        return result != null && result.complete();
    }
    public record Result(int posts, int uploads, boolean complete, boolean dryRun) {}
}
