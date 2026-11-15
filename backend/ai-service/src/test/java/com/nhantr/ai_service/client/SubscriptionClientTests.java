package com.nhantr.ai_service.client;

import com.nhantr.common.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.nhantr.common.web.RequestIdFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class SubscriptionClientTests {
    HttpServer server;
    String body;
    int status = 200;
    final AtomicReference<String> key = new AtomicReference<>(), trace = new AtomicReference<>();
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/v1/subscriptions/7/entitlements", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            trace.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
    }
    @AfterEach void cleanup() { if (server != null) server.stop(0); }
    @Test void forwardsInternalKeyAndRequestIdOnLiveEntitlementCheck() throws Exception {
        body = "{\"aiGeneration\":true,\"validUntil\":\"" + Instant.now().plusSeconds(60) + "\"}";
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "ai-test-trace");
        new RequestIdFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> client().requireAi(7L));
        assertThat(key.get()).isEqualTo("internal-test-key");
        assertThat(trace.get()).isEqualTo("ai-test-trace");
    }
    @Test void falseMissingExpiredMalformedOrUnavailableGrantAllDeny() {
        for (String denied : new String[]{"{}", "null", "{\"aiGeneration\":false,\"validUntil\":\"2099-01-01T00:00:00Z\"}",
                "{\"aiGeneration\":true,\"validUntil\":\"2000-01-01T00:00:00Z\"}", "not-json"}) {
            body=denied;
            assertThatThrownBy(() -> client().requireAi(7L)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("AI_TIER_REQUIRED");
        }
        body="{}"; status=503;
        assertThatThrownBy(() -> client().requireAi(7L)).isInstanceOf(ApiException.class);
    }
    SubscriptionClient client() { return new SubscriptionClient("http://localhost:" + server.getAddress().getPort(), "internal-test-key"); }
}
