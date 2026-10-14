package com.nhantr.identity_service.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {
    private final JwtService jwt = new JwtService(
            "test-secret-key-that-is-longer-than-thirty-two-bytes",
            15,
            30
    );

    @Test
    void createsAccessTokenWithExpectedSubjectAndType() {
        String token = jwt.createAccessToken(42L);
        var claims = jwt.parseToken(token);
        assertThat(jwt.getUserId(claims)).isEqualTo(42L);
        assertThat(jwt.getTokenType(claims)).isEqualTo("access");
    }

    @Test
    void createsRefreshTokenWithStableTokenId() {
        String token = jwt.createRefreshToken(42L, "refresh-1");
        var claims = jwt.parseToken(token);
        assertThat(jwt.getTokenId(claims)).isEqualTo("refresh-1");
        assertThat(jwt.getTokenType(claims)).isEqualTo("refresh");
    }
}
