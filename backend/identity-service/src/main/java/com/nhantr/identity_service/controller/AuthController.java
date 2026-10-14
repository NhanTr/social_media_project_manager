package com.nhantr.identity_service.controller;

import com.nhantr.identity_service.dto.request.GoogleLoginRequest;
import com.nhantr.identity_service.dto.request.PhoneOtpRequest;
import com.nhantr.identity_service.dto.request.RefreshTokenRequest;
import com.nhantr.identity_service.dto.request.VerifyPhoneOtpRequest;
import com.nhantr.identity_service.dto.response.AuthResponse;
import com.nhantr.identity_service.dto.response.OtpResponse;
import com.nhantr.identity_service.service.AuthService;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
    }


    @PostMapping("/phone/send-otp")
    public ResponseEntity<OtpResponse> requestPhoneOtp(@Valid @RequestBody PhoneOtpRequest request) {
        return ResponseEntity.ok(authService.requestPhoneOtp(request));
    }

    @PostMapping("/phone/verify-otp")
    public ResponseEntity<AuthResponse> verifyPhoneOtp(@Valid @RequestBody VerifyPhoneOtpRequest request) {
        return ResponseEntity.ok(authService.verifyPhoneOtp(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
