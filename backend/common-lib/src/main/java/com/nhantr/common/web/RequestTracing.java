package com.nhantr.common.web;

import org.springframework.web.client.RestClient;

/** Synchronous internal requests share the sanitized ingress ID; no credentials enter telemetry. */
public final class RequestTracing {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private RequestTracing() {}
    static void setCurrentRequestId(String id) { CURRENT.set(id); }
    static void clear() { CURRENT.remove(); }
    public static String currentRequestId() { return CURRENT.get(); }
    public static RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestInterceptor((request, body, execution) -> {
            String id = currentRequestId();
            if (id != null) request.getHeaders().set("X-Request-Id", id);
            return execution.execute(request, body);
        });
    }
}
