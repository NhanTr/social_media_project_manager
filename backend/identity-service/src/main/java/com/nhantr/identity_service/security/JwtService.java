package com.nhantr.identity_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-minutes}") long accessTokenExpirationMinutes,
            @Value("${app.jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenExpirationMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenExpirationDays);
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, "access", accessTokenTtl, UUID.randomUUID().toString());
    }

    public String createRefreshToken(Long userId, String tokenId) {
        return createToken(userId, "refresh", refreshTokenTtl, tokenId);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getTokenType(Claims claims) {
        return claims.get("token_type", String.class);
    }

    public String getTokenId(Claims claims) {
        return claims.getId();
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    private String createToken(Long userId, String tokenType, Duration ttl, String tokenId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        return Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(userId))
                .claim("token_type", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }
}
