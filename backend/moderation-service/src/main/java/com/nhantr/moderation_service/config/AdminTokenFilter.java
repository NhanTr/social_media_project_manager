package com.nhantr.moderation_service.config;

import com.nhantr.common.security.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Role claims on a user access token never grant administrative privileges. */
public class AdminTokenFilter extends OncePerRequestFilter {
    private final JwtTokenService jwt;
    public AdminTokenFilter(JwtTokenService jwt) { this.jwt = jwt; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/v1/admin/")
                && !request.getRequestURI().equals("/api/v1/admin/auth/login")) {
            String header = request.getHeader("Authorization");
            try {
                if (header == null || !header.startsWith("Bearer ")
                        || !"admin".equals(jwt.parse(header.substring(7)).get("token_type", String.class))) {
                    SecurityContextHolder.clearContext();
                    response.sendError(403); return;
                }
            } catch (RuntimeException invalid) {
                SecurityContextHolder.clearContext(); response.sendError(403); return;
            }
        }
        chain.doFilter(request, response);
    }
}
