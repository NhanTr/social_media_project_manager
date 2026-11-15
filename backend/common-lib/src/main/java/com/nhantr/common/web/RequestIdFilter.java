package com.nhantr.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@org.springframework.core.annotation.Order(-200)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || !requestId.matches("[A-Za-z0-9._-]{1,64}")) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader("X-Request-Id", requestId);
        request.setAttribute("requestId", requestId);
        RequestTracing.setCurrentRequestId(requestId);
        MDC.put("requestId", requestId);
        try {
            final String propagatedId = requestId;
            var wrapped = new jakarta.servlet.http.HttpServletRequestWrapper(request) {
                @Override public String getHeader(String name) {
                    return "X-Request-Id".equalsIgnoreCase(name) ? propagatedId : super.getHeader(name);
                }
                @Override public java.util.Enumeration<String> getHeaders(String name) {
                    return "X-Request-Id".equalsIgnoreCase(name) ? java.util.Collections.enumeration(java.util.List.of(propagatedId)) : super.getHeaders(name);
                }
                @Override public java.util.Enumeration<String> getHeaderNames() {
                    java.util.Set<String> names = new java.util.LinkedHashSet<>(java.util.Collections.list(super.getHeaderNames()));
                    names.removeIf(name -> "X-Request-Id".equalsIgnoreCase(name));
                    names.add("X-Request-Id");
                    return java.util.Collections.enumeration(names);
                }
            };
            chain.doFilter(wrapped, response);
        } finally {
            MDC.remove("requestId");
            RequestTracing.clear();
        }
    }
}
