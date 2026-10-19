package com.nhantr.identity_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.identity_service.dto.request.PhoneOtpRequest;
import com.nhantr.identity_service.dto.response.OtpResponse;
import com.nhantr.identity_service.repository.UserRepository;
import com.nhantr.identity_service.security.GoogleTokenVerifier;
import com.nhantr.identity_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceOtpRateLimitTests {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AuthService authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService = new AuthService(
                mock(UserRepository.class),
                redisTemplate,
                mock(JwtService.class),
                5L,
                true,
                mock(GoogleTokenVerifier.class),
                mock(TermsService.class),
                5L
        );
    }

    @Test
    void firstRequestWithinLimitStoresOtpAndOpensRateWindow() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        OtpResponse response = authService.requestPhoneOtp(new PhoneOtpRequest("+84901234567"));

        assertThat(response.devOtp()).isNotNull().matches("\\d{6}");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
        verify(redisTemplate).expire(anyString(), any(Duration.class));
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void requestBeyondLimitIsRejectedWith429AndKeepsOtpUntouched() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        ApiException error = catchThrowableOfType(
                () -> authService.requestPhoneOtp(new PhoneOtpRequest("+84901234567")),
                ApiException.class
        );

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("OTP_RATE_LIMITED");
        assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void fifthRequestIsStillAllowedWhenLimitIsFive() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        OtpResponse response = authService.requestPhoneOtp(new PhoneOtpRequest("+84901234567"));

        assertThat(response.devOtp()).isNotNull();
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }
}