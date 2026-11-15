package com.nhantr.post_service.client;

import com.nhantr.common.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class AiClientTests {
    @Test void onlyMatchingOwnerSuccessfulGenerationAndExactMediaCanBePosted() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        var body = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/internal/v1/ai/generations/15", exchange -> {
            if (!"test-key".equals(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"))
                    || !"userId=7".equals(exchange.getRequestURI().getQuery())) {
                exchange.sendResponseHeaders(403, -1); exchange.close(); return;
            }
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var client = new AiClient("http://localhost:" + server.getAddress().getPort(), "test-key");
            for (String denied : new String[]{"{}", "{\"id\":15,\"userId\":8,\"status\":\"SUCCESS\",\"generatedMediaUrl\":\"https://media.test/result\"}",
                    "{\"id\":15,\"userId\":7,\"status\":\"PROCESSING\",\"generatedMediaUrl\":\"https://media.test/result\"}",
                    "{\"id\":15,\"userId\":7,\"status\":\"SUCCESS\",\"generatedMediaUrl\":\"https://media.test/other\"}"}) {
                body.set(denied);
                assertThatThrownBy(() -> client.validate(7L, 15L, "https://media.test/result")).isInstanceOf(ApiException.class);
            }
            body.set("""
                    {"id":15,"userId":7,"status":"SUCCESS","generatedMediaUrl":"https://media.test/result",
                     "originalMediaUrl":"https://media.test/original","stylePrompt":"anime","generationType":"IMAGE_TO_IMAGE",
                     "errorMessage":null,"createdAt":"2026-11-15T00:00:00Z","completedAt":"2026-11-15T00:00:01Z"}
                    """);
            assertThatCode(() -> client.validate(7L, 15L, "https://media.test/result")).doesNotThrowAnyException();
        } finally { server.stop(0); }
    }
}
