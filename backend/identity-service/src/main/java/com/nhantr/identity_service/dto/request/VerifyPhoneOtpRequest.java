package com.nhantr.identity_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyPhoneOtpRequest(
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{8,15}$") String phoneNumber,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp,
        @Size(max = 100) String displayName
) {
}
