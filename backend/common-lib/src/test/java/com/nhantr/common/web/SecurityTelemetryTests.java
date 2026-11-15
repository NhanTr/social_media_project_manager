package com.nhantr.common.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class SecurityTelemetryTests {
    @Test
    void internalTrafficHasAnIndependentBoundedBudget() throws Exception {
        var limiter = new LocalRateLimitFilter(2, 1, 10, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        limiter.doFilter(new MockHttpServletRequest("GET", "/internal/v1/users/1"), new MockHttpServletResponse(), (req, res) -> {});
        var limited = new MockHttpServletResponse();
        limiter.doFilter(new MockHttpServletRequest("GET", "/internal/v1/posts/1"), limited, (req, res) -> fail("shared internal budget"));
        assertEquals(429, limited.getStatus());
        limiter.doFilter(new MockHttpServletRequest("GET", "/api/v1/posts"), new MockHttpServletResponse(), (req, res) -> {});
    }

    @Test
    void unexpectedExceptionLogNeverIncludesExceptionSecretsOrRawRequestId() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(com.nhantr.common.api.GlobalExceptionHandler.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/private@example.com");
            request.addHeader("X-Request-Id", "unsafe-secret"); request.setAttribute("requestId", "safe-normalized");
            var result = new com.nhantr.common.api.GlobalExceptionHandler().handleUnexpected(new RuntimeException("payment-token-secret"), request);
            var event = appender.list.getFirst();
            assertFalse(event.getFormattedMessage().contains("private@example.com"));
            assertFalse(event.getFormattedMessage().contains("unsafe-secret"));
            assertNull(event.getThrowableProxy());
            assertEquals("safe-normalized", result.getBody().getProperties().get("requestId"));
        } finally { logger.detachAppender(appender); }
    }

    @Test
    void internalRestClientReceivesSanitizedRequestId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "trace-safe-123");
        new RequestIdFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var builder = RequestTracing.restClientBuilder();
            var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
            server.expect(outbound -> {
                        assertEquals("http://example.test/internal", outbound.getURI().toString());
                        assertEquals("trace-safe-123", outbound.getHeaders().getFirst("X-Request-Id"));
                    })
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess());
            builder.build().get().uri("http://example.test/internal").retrieve().toBodilessEntity();
            server.verify();
        });
    }

    @Test
    void requestLogExcludesCredentialsBodyQueryAndPathIdentifiers() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RequestTelemetryFilter.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/secret@example.com");
            request.setQueryString("otp=123456&payment_token=private");
            request.addHeader("Authorization", "Bearer sensitive-token");
            request.addHeader("X-Internal-Api-Key", "sensitive-key");
            request.setContent("{password:private,otp:123456}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var registry = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
            new RequestIdFilter().doFilter(request, new MockHttpServletResponse(), (req, res) ->
                    new RequestTelemetryFilter(registry.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class))
                            .doFilter(req, res, (r, s2) -> {}));
            String logged = appender.list.getFirst().getFormattedMessage();
            assertTrue(logged.contains("event=http_request"));
            for (String secret : java.util.List.of("secret@example.com", "123456", "private", "sensitive-token", "sensitive-key")) assertFalse(logged.contains(secret));
        } finally { logger.detachAppender(appender); }
    }

    @Test
    void rejectsUnsafeRequestIdAndPropagatesNormalizedValueDownstream() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "secret\r\nAuthorization: bad");
        var response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response, (req, res) -> {
            String id = response.getHeader("X-Request-Id");
            assertTrue(id.matches("[A-Za-z0-9._-]{1,64}"));
            assertEquals(id, req.getAttribute("requestId"));
            assertEquals(id, ((jakarta.servlet.http.HttpServletRequest) req).getHeader("X-Request-Id"));
            assertEquals(id, RequestTracing.currentRequestId());
        });
        assertNull(RequestTracing.currentRequestId());
    }

    @Test
    void rateLimitBoundsIdentityMemoryAndDoesNotTrustForwardedHeaders() throws Exception {
        var limiter = new LocalRateLimitFilter(1, 1, 1, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.setRemoteAddr("127.0.0.1");
        limiter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "different");
        var response = new MockHttpServletResponse();
        limiter.doFilter(request, response, (req, res) -> fail("limited"));
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.setRemoteAddr("127.0.0.2");
        limiter.doFilter(request, new MockHttpServletResponse(), (req, res) -> fail("identity cap"));
    }

    @Test
    void telemetryUsesOnlyAllowlistedMethodAndCategory() {
        assertEquals("other", RequestTelemetryFilter.category("/api/v1/secret-email@example.com?otp=123456"));
        assertEquals("auth", RequestTelemetryFilter.category("/api/v1/auth/otp/123456"));
        assertEquals("OTHER", RequestTelemetryFilter.safeMethod("secret"));
    }
}
