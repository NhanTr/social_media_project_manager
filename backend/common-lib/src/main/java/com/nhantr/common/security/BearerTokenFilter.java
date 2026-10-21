package com.nhantr.common.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class BearerTokenFilter extends OncePerRequestFilter {
    private final JwtTokenService jwt;
    private final String internalApiKey;

    public BearerTokenFilter(JwtTokenService jwt, String internalApiKey) {
        this.jwt = jwt;
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/internal/")) {
            String supplied = request.getHeader("X-Internal-Api-Key");
            if (internalApiKey != null && !internalApiKey.isBlank() && internalApiKey.equals(supplied)) {
                authenticate(new CurrentUser(-1L, "SERVICE", true), "ROLE_SERVICE");
            }
            chain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                var claims = jwt.parse(authorization.substring(7));
                String tokenType = claims.get("token_type", String.class);
                if ("access".equals(tokenType) || "admin".equals(tokenType)) {
                    String role = claims.get("role", String.class);
                    boolean termsAccepted = "admin".equals(tokenType)
                            || Boolean.TRUE.equals(claims.get("terms_accepted", Boolean.class));
                    authenticate(
                            new CurrentUser(Long.valueOf(claims.getSubject()), role, termsAccepted),
                            "ROLE_" + (role == null ? "USER" : role)
                    );
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(CurrentUser user, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                )
        );
    }
}
