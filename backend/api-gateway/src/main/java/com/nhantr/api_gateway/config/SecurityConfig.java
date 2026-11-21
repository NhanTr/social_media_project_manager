package com.nhantr.api_gateway.config;

import com.nhantr.api_gateway.filter.TermsAcceptanceFilter;
import com.nhantr.common.security.BearerTokenFilter;
import com.nhantr.common.security.JwtTokenService;
import com.nhantr.common.security.SecuritySupport;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    JwtTokenService gatewayJwtTokenService(@Value("${app.jwt.secret}") String secret) {
        return new JwtTokenService(secret);
    }

    @Bean
    TermsAcceptanceFilter termsAcceptanceFilter(ObjectMapper objectMapper) {
        return new TermsAcceptanceFilter(objectMapper);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenService jwt,
            TermsAcceptanceFilter termsAcceptanceFilter,
            @Value("${app.internal-api-key:loccoc-local-internal-key}") String internalApiKey
    ) throws Exception {
        return SecuritySupport.serviceChain(
                http,
                new BearerTokenFilter(jwt, internalApiKey),
                termsAcceptanceFilter,
                "/api/v1/auth/**",
                "/api/v1/terms/latest",
                "/ws/chat/**"
        );
    }
}
