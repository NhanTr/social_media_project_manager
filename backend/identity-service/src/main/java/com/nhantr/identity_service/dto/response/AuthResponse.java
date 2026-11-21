package com.nhantr.identity_service.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user,
        boolean requiresTermsAcceptance
) {
}
