package com.nhantr.identity_service.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$")
        String username,
        @Size(max = 100) String displayName,
        @Size(max = 2048) String avatarUrl,
        @Size(max = 500) String bio
) {
}
