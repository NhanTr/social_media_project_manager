package com.nhantr.identity_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.identity_service.dto.request.*;
import com.nhantr.identity_service.dto.response.*;
import com.nhantr.identity_service.entity.User;
import com.nhantr.identity_service.enums.AuthProvider;
import com.nhantr.identity_service.enums.UserStatus;
import com.nhantr.identity_service.repository.UserRepository;
import com.nhantr.identity_service.security.GoogleTokenVerifier;
import com.nhantr.identity_service.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;


@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final JwtService jwtService;
    private final long otpExpirationMinutes;
    private final boolean exposeOtpCode;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final TermsService termsService;
    private final long resendLimitPerMinute;

    public AuthService(
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            JwtService jwtService,
            @Value("${app.otp.expiration-minutes}") long otpExpirationMinutes,
            @Value("${app.otp.expose-code:false}") boolean exposeOtpCode,
            GoogleTokenVerifier googleTokenVerifier,
            TermsService termsService,
            @Value("${app.otp.resend-limit-per-minute:5}") long resendLimitPerMinute
    ) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.jwtService = jwtService;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.exposeOtpCode = exposeOtpCode;
        this.googleTokenVerifier = googleTokenVerifier;
        this.termsService = termsService;
        this.resendLimitPerMinute = resendLimitPerMinute;
    }

    public OtpResponse requestPhoneOtp(PhoneOtpRequest request) {
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        enforceResendLimit(phoneNumber);
        String otp = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));

        redisTemplate.opsForValue().set(otpKey(phoneNumber), otp, Duration.ofMinutes(otpExpirationMinutes));

        return new OtpResponse(
                "OTP has been created for the phone login flow.",
                otpExpirationMinutes * 60,
                exposeOtpCode ? otp : null
        );
    }

    private void enforceResendLimit(String phoneNumber) {
        String rateKey = otpRateKey(phoneNumber);
        Long attempts = redisTemplate.opsForValue().increment(rateKey);
        if (attempts == null) {
            return;
        }
        if (attempts == 1L) {
            redisTemplate.expire(rateKey, Duration.ofMinutes(1));
        }
        if (attempts > resendLimitPerMinute) {
            throw ApiException.tooManyRequests(
                    "OTP_RATE_LIMITED",
                    "Too many OTP requests for this phone number. Please wait and try again."
            );
        }
    }

    @Transactional
    public AuthResponse verifyPhoneOtp(VerifyPhoneOtpRequest request) {
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        String submittedOtp = requireText(request.otp(), "otp");
        String savedOtp = redisTemplate.opsForValue().get(otpKey(phoneNumber));

        if (!submittedOtp.equals(savedOtp)) {
            throw new IllegalArgumentException("OTP is invalid or expired.");
        }

        redisTemplate.delete(otpKey(phoneNumber));

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> createPhoneUser(phoneNumber, request.displayName()));
        user.setLastLoginAt(Instant.now());
        User savedUser = userRepository.save(user);

        return createAuthResponse(savedUser);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = requireText(request.refreshToken(), "refreshToken");
        Claims claims = jwtService.parseToken(refreshToken);

        if (!"refresh".equals(jwtService.getTokenType(claims))) {
            throw new IllegalArgumentException("Refresh token is invalid.");
        }

        Long userId = jwtService.getUserId(claims);
        String tokenId = jwtService.getTokenId(claims);
        String refreshKey = refreshTokenKey(userId, tokenId);
        String savedToken = redisTemplate.opsForValue().get(refreshKey);

        if (!refreshToken.equals(savedToken)) {
            throw new IllegalArgumentException("Refresh token is expired or revoked.");
        }

        redisTemplate.delete(refreshKey);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        requireActive(user);
        return createAuthResponse(user);
    }

    public void logout(RefreshTokenRequest request) {
        String refreshToken = requireText(request.refreshToken(), "refreshToken");
        Claims claims = jwtService.parseToken(refreshToken);

        if ("refresh".equals(jwtService.getTokenType(claims))) {
            redisTemplate.delete(refreshTokenKey(jwtService.getUserId(claims), jwtService.getTokenId(claims)));
        }
    }

    private AuthResponse createAuthResponse(User user) {
        requireActive(user);
        String refreshTokenId = UUID.randomUUID().toString();
        String accessToken = jwtService.createAccessToken(user.getId());
        String refreshToken = jwtService.createRefreshToken(user.getId(), refreshTokenId);

        redisTemplate.opsForValue().set(
                refreshTokenKey(user.getId(), refreshTokenId),
                refreshToken,
                jwtService.getRefreshTokenTtl()
        );

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenTtl().toSeconds(),
                UserResponse.from(user),
                !termsService.hasAcceptedLatest(user.getId())
        );
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        String idToken = requireText(request.idToken(), "idToken");
        var payload = googleTokenVerifier.verify(idToken);

        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String displayName = (String) payload.get("name");
        String avatarUrl = (String) payload.get("picture");

        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> createGoogleUser(googleId, email, displayName, avatarUrl));

        user.setLastLoginAt(Instant.now());

        if (user.getEmail() == null && email != null) {
            user.setEmail(email);
        }

        if (user.getDisplayName() == null && displayName != null) {
            user.setDisplayName(displayName);
        }

        if (user.getAvatarUrl() == null && avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }

        User savedUser = userRepository.save(user);
        return createAuthResponse(savedUser);
    }

    private User createGoogleUser(
        String googleId,
        String email,
        String displayName,
        String avatarUrl
    ) {
        User user = new User();
        user.setGoogleId(googleId);
        user.setEmail(email);
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setUsername(generateGoogleUsername(email));
        user.setDisplayName(resolveGoogleDisplayName(displayName, email));
        user.setAvatarUrl(avatarUrl);
        user.setFriendInviteCode(generateFriendInviteCode());
        return user;
    }
    private String generateGoogleUsername(String email) {
        String base;

        if (email != null && email.contains("@")) {
            base = email.substring(0, email.indexOf("@"));
        } else {
            base = "google_user";
        }

        base = base.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");

        String username = base;

        while (userRepository.existsByUsername(username)) {
            username = base + "_" + SECURE_RANDOM.nextInt(10_000);
        }

        return username;
    }

    private String resolveGoogleDisplayName(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }

        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }

        return "Google User";
    }

    private User createPhoneUser(String phoneNumber, String displayName) {
        User user = new User();
        user.setPhoneNumber(phoneNumber);
        user.setAuthProvider(AuthProvider.PHONE);
        user.setUsername(generateUsername(phoneNumber));
        user.setDisplayName(resolveDisplayName(displayName, phoneNumber));
        user.setFriendInviteCode(generateFriendInviteCode());
        return user;
    }

    private String generateUsername(String phoneNumber) {
        String digits = phoneNumber.replaceAll("\\D", "");
        String base = "user_" + (digits.length() > 10 ? digits.substring(digits.length() - 10) : digits);
        String username = base;

        while (userRepository.existsByUsername(username)) {
            username = base + "_" + SECURE_RANDOM.nextInt(10_000);
        }

        return username;
    }

    private String generateFriendInviteCode() {
        String code;

        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        } while (userRepository.existsByFriendInviteCode(code));

        return code;
    }

    private String resolveDisplayName(String displayName, String phoneNumber) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }

        String digits = phoneNumber.replaceAll("\\D", "");
        String suffix = digits.length() > 4 ? digits.substring(digits.length() - 4) : digits;
        return "User " + suffix;
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = requireText(phoneNumber, "phoneNumber").replaceAll("\\s+", "");

        if (!normalized.matches("^\\+?[0-9]{8,15}$")) {
            throw new IllegalArgumentException("phoneNumber must contain 8 to 15 digits.");
        }

        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        return value.trim();
    }

    private String otpKey(String phoneNumber) {
        return "identity:otp:phone:" + phoneNumber;
    }

    private String otpRateKey(String phoneNumber) {
        return "identity:otp:rate:" + phoneNumber;
    }

    private String refreshTokenKey(Long userId, String tokenId) {
        return "identity:refresh-token:" + userId + ":" + tokenId;
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active.");
        }
    }
}
