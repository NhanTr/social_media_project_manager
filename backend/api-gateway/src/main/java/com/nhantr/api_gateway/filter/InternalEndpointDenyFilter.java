package com.nhantr.api_gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Internal credentials must never open internal endpoints on the public ingress. */
@Component
@Order(-190)
public class InternalEndpointDenyFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/internal") || path.startsWith("/internal/")) {
            response.setStatus(404);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":404,\"code\":\"NOT_FOUND\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
