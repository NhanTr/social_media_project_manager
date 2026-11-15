package com.nhantr.moderation_service.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

class NotificationClientTests {
    @Test void warningAndReporterResultUseSeparateStableKeysAndServiceAuthentication() throws Exception {
        var keys = new CopyOnWriteArrayList<String>();
        var credentials = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/notifications/report-result", request -> {
            keys.add(request.getRequestHeaders().getFirst("Idempotency-Key"));
            credentials.add(request.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            request.getRequestBody().readAllBytes(); request.sendResponseHeaders(200, -1); request.close();
        });
        server.start();
        try {
            var client = new NotificationClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-only-key");
            client.reportResult(1L, 3L, "Resolved"); client.reportResult(1L, 3L, "Resolved");
            client.warning(2L, 3L, "Warning"); client.warning(2L, 3L, "Warning");
            assertThat(keys).hasSize(4);
            assertThat(keys.get(0)).isEqualTo(keys.get(1)).isNotEqualTo(keys.get(2));
            assertThat(keys.get(2)).isEqualTo(keys.get(3));
            assertThat(credentials).containsOnly("test-only-key");
        } finally { server.stop(0); }
    }
    @Test void notificationFailurePropagatesSoDurableMarkerRemainsPending() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/notifications/report-result", request -> {
            request.getRequestBody().readAllBytes(); request.sendResponseHeaders(503, -1); request.close();
        });
        server.start();
        try {
            var client = new NotificationClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-only-key");
            assertThatThrownBy(() -> client.reportResult(1L, 3L, "Resolved")).isInstanceOf(RuntimeException.class);
        } finally { server.stop(0); }
    }
}
