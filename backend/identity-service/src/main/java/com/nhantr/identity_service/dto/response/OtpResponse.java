package com.nhantr.identity_service.dto.response;

public record OtpResponse(
        String message,
        long expiresInSeconds,
        String devOtp
) {
}
