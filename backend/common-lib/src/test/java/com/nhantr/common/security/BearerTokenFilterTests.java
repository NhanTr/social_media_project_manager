package com.nhantr.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearerTokenFilterTests {
    private static final String SECRET = "test-secret-key-that-is-longer-than-thirty-two-bytes";
    private final BearerTokenFilter filter = new BearerTokenFilter(new JwtTokenService(SECRET), "internal-key");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsAcceptedTermsClaimToPrincipal() throws Exception {
        authenticate("access", "USER", true);

        assertTrue(currentUser().termsAccepted());
    }

    @Test
    void treatsLegacyTokenWithoutTermsClaimAsUnaccepted() throws Exception {
        authenticate("access", "USER", null);

        assertFalse(currentUser().termsAccepted());
    }

    @Test
    void adminTokenBypassesTermsGate() throws Exception {
        authenticate("admin", "SUPER_ADMIN", false);

        assertTrue(currentUser().termsAccepted());
    }

    @Test
    void internalServiceAuthenticationBypassesTermsGate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/users/42");
        request.addHeader("X-Internal-Api-Key", "internal-key");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertTrue(currentUser().termsAccepted());
    }

    private void authenticate(String tokenType, String role, Boolean termsAccepted) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profile");
        request.addHeader("Authorization", "Bearer " + token(tokenType, role, termsAccepted));
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private CurrentUser currentUser() {
        return (CurrentUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String token(String tokenType, String role, Boolean termsAccepted) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject("42")
                .claim("token_type", tokenType)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)));
        if (termsAccepted != null) {
            builder.claim("terms_accepted", termsAccepted);
        }
        return builder
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
