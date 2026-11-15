package com.nhantr.common.web;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Closed field allowlist: never log URL/query, headers, body, principal or exception messages. */
@Component
@Order(-180)
public class RequestTelemetryFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTelemetryFilter.class);
    private static final Set<String> CATEGORIES = Set.of("auth", "terms", "posts", "media", "subscriptions", "ai", "reports", "admin", "account", "users", "friends", "notifications", "chat");
    private final ObjectProvider<MeterRegistry> registries;
    public RequestTelemetryFilter(ObjectProvider<MeterRegistry> registries) { this.registries = registries; }
    static String category(String path) {
        if (path == null || !path.startsWith("/api/v1/")) return "other";
        String value = path.substring(8).split("/", 2)[0];
        return CATEGORIES.contains(value) ? value : "other";
    }
    static String safeMethod(String method) { return Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD").contains(method) ? method : "OTHER"; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try { chain.doFilter(request, response); }
        finally {
            long elapsed = System.nanoTime() - start;
            String method = safeMethod(request.getMethod());
            String category = category(request.getRequestURI());
            String status = Integer.toString(response.getStatus());
            LOG.info("event=http_request request_id={} method={} category={} status={} duration_ms={}",
                    request.getAttribute("requestId"), method, category, status, TimeUnit.NANOSECONDS.toMillis(elapsed));
            registries.ifAvailable(registry -> registry.timer("loccoc.http.requests", "method", method, "category", category, "status", status).record(elapsed, TimeUnit.NANOSECONDS));
        }
    }
}
