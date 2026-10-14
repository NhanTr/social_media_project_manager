package com.nhantr.moderation_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.common.security.JwtTokenService;
import com.nhantr.moderation_service.repository.AdminRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AdminAuthService {
    private final AdminRepository repository;
    private final PasswordEncoder encoder;
    private final JwtTokenService jwt;

    public AdminAuthService(AdminRepository repository, PasswordEncoder encoder, JwtTokenService jwt) {
        this.repository = repository;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AdminAuthResponse login(AdminLoginRequest request) {
        var admin = repository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> ApiException.forbidden("ADMIN_LOGIN_INVALID", "Username or password is invalid."));
        if (!encoder.matches(request.password(), admin.getPasswordHash())) {
            throw ApiException.forbidden("ADMIN_LOGIN_INVALID", "Username or password is invalid.");
        }
        String token = jwt.create(admin.getId(), "admin", admin.getRole().name(), Duration.ofHours(8));
        return new AdminAuthResponse(token, "Bearer", 8 * 60 * 60L, admin.getRole().name());
    }

    public record AdminLoginRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 200) String password
    ) {}
    public record AdminAuthResponse(String accessToken, String tokenType, long expiresInSeconds, String role) {}
}
