package com.nhantr.identity_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneOtpRequest(
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "phoneNumber must contain 8 to 15 digits")
        String phoneNumber
) {
}
