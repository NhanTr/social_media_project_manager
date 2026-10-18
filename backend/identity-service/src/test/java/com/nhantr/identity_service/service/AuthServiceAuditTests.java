package com.nhantr.identity_service.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.nhantr.common.api.ApiException;
import com.nhantr.identity_service.dto.request.GoogleLoginRequest;
import com.nhantr.identity_service.dto.request.PhoneOtpRequest;
import com.nhantr.identity_service.dto.request.RefreshTokenRequest;
import com.nhantr.identity_service.dto.request.VerifyPhoneOtpRequest;
import com.nhantr.identity_service.entity.TermsVersion;
import com.nhantr.identity_service.entity.User;
import com.nhantr.identity_service.enums.AuthProvider;
import com.nhantr.identity_service.repository.TermsVersionRepository;
import com.nhantr.identity_service.repository.UserRepository;
import com.nhantr.identity_service.repository.UserTermsAcceptanceRepository;
import com.nhantr.identity_service.security.GoogleTokenVerifier;
import com.nhantr.identity_service.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AuthServiceAuditTests {
    private UserRepository users;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private JwtService jwt;
    private GoogleTokenVerifier google;
    private TermsService terms;
    private AuthAuditLogger audit;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        jwt = mock(JwtService.class);
        google = mock(GoogleTokenVerifier.class);
        terms = mock(TermsService.class);
        audit = mock(AuthAuditLogger.class);
        when(redis.opsForValue()).thenReturn(values);
        auth = new AuthService(users, redis, jwt, 5, false, google, terms, audit);
    }

    @Test
    void auditsOtpRequestAcceptedAndRateLimited() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true, false);

        auth.requestPhoneOtp(new PhoneOtpRequest("+84900000001"));
        assertThatThrownBy(() -> auth.requestPhoneOtp(new PhoneOtpRequest("+84900000001")))
                .isInstanceOf(ApiException.class);

        verify(audit).record("otp_request", "accepted", null);
        verify(audit).record("otp_request", "rate_limited", null);
    }

    @Test
    void auditFailureDoesNotChangeAuthenticationOutcome() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).record("otp_request", "accepted", null);

        assertThatCode(() -> auth.requestPhoneOtp(new PhoneOtpRequest("+84900000001")))
                .doesNotThrowAnyException();
    }

    @Test
    void auditsOtpVerificationSuccessInvalidAndAttemptLimit() {
        User user = user(42L, AuthProvider.PHONE);
        when(redis.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(1L, 0L, -1L);
        when(users.findByPhoneNumber(anyString())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        stubAuthResponse(user);

        auth.verifyPhoneOtp(new VerifyPhoneOtpRequest("+84900000001", "654321", null));
        assertThatThrownBy(() -> auth.verifyPhoneOtp(
                new VerifyPhoneOtpRequest("+84900000001", "111111", null)
        )).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> auth.verifyPhoneOtp(
                new VerifyPhoneOtpRequest("+84900000001", "222222", null)
        )).isInstanceOf(ApiException.class);

        verify(audit).record("otp_verification", "succeeded", 42L);
        verify(audit).record("otp_verification", "failed", null);
        verify(audit).record("otp_verification", "attempt_limit", null);
    }

    @Test
    void auditsGoogleLoginSuccessAndVerificationFailure() {
        User user = user(42L, AuthProvider.GOOGLE);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload()
                .setSubject("google-123")
                .setEmail("person@example.com");
        payload.set("name", "Tester");
        when(google.verify("google-payload")).thenReturn(payload);
        when(users.findByGoogleId("google-123")).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        stubAuthResponse(user);

        auth.loginWithGoogle(new GoogleLoginRequest("google-payload"));
        when(google.verify("invalid-google-payload"))
                .thenThrow(new IllegalArgumentException("invalid"));
        assertThatThrownBy(() -> auth.loginWithGoogle(
                new GoogleLoginRequest("invalid-google-payload")
        )).isInstanceOf(IllegalArgumentException.class);

        verify(audit).record("google_login", "succeeded", 42L);
        verify(audit).record("google_login", "failed", null);
    }

    @Test
    void auditsRefreshSuccessAndRejection() {
        User user = user(42L, AuthProvider.PHONE);
        Claims claims = refreshClaims(42L, "refresh-1");
        when(jwt.parseToken("refresh-secret")).thenReturn(claims);
        when(values.getAndDelete("identity:refresh-token:42:refresh-1"))
                .thenReturn("refresh-secret", (String) null);
        when(users.findById(42L)).thenReturn(Optional.of(user));
        stubAuthResponse(user);

        auth.refreshToken(new RefreshTokenRequest("refresh-secret"));
        assertThatThrownBy(() -> auth.refreshToken(new RefreshTokenRequest("refresh-secret")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(audit).record("token_refresh", "succeeded", 42L);
        verify(audit).record("token_refresh", "rejected", 42L);
    }

    @Test
    void auditsLogoutRevokedAndAbsent() {
        Claims claims = refreshClaims(42L, "refresh-1");
        when(jwt.parseToken("refresh-secret")).thenReturn(claims);
        when(redis.delete("identity:refresh-token:42:refresh-1"))
                .thenReturn(true, false);

        auth.logout(new RefreshTokenRequest("refresh-secret"));
        auth.logout(new RefreshTokenRequest("refresh-secret"));

        verify(audit).record("logout", "revoked", 42L);
        verify(audit).record("logout", "absent", 42L);
    }

    @Test
    void auditsTermsAcceptance() {
        TermsVersionRepository versions = mock(TermsVersionRepository.class);
        UserTermsAcceptanceRepository acceptances = mock(UserTermsAcceptanceRepository.class);
        TermsVersion latest = mock(TermsVersion.class);
        when(users.existsById(42L)).thenReturn(true);
        when(versions.findFirstByActiveTrueOrderByPublishedAtDesc()).thenReturn(Optional.of(latest));
        when(latest.getId()).thenReturn(7L);

        new TermsService(versions, acceptances, users, audit).accept(42L, 7L);

        verify(audit).record("terms_acceptance", "succeeded", 42L);
    }

    private void stubAuthResponse(User user) {
        when(terms.hasAcceptedLatest(user.getId())).thenReturn(false);
        when(jwt.createAccessToken(user.getId(), false)).thenReturn("access-secret");
        when(jwt.createRefreshToken(anyLong(), anyString())).thenReturn("refresh-secret");
        when(jwt.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(jwt.getRefreshTokenTtl()).thenReturn(Duration.ofDays(30));
    }

    private Claims refreshClaims(Long userId, String tokenId) {
        Claims claims = mock(Claims.class);
        when(jwt.getTokenType(claims)).thenReturn("refresh");
        when(jwt.getUserId(claims)).thenReturn(userId);
        when(jwt.getTokenId(claims)).thenReturn(tokenId);
        return claims;
    }

    private User user(Long id, AuthProvider provider) {
        User user = new User();
        user.setId(id);
        user.setUsername("tester");
        user.setDisplayName("Tester");
        user.setAuthProvider(provider);
        return user;
    }
}
