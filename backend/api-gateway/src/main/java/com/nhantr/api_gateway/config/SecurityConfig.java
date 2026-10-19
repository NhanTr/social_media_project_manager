package com.nhantr.api_gateway.config;

import com.nhantr.common.security.BearerTokenFilter;
import com.nhantr.common.security.JwtTokenService;
import com.nhantr.common.security.SecuritySupport;
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
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenService jwt,
            @Value("${app.internal-api-key:loccoc-local-internal-key}") String internalApiKey
    ) throws Exception {
        return SecuritySupport.serviceChain(
                http,
                new BearerTokenFilter(jwt, internalApiKey),
                "/api/v1/auth/**",
                "/api/v1/terms/latest",
                "/api/v1/admin/auth/login",
                "/ws/chat/**"
        );
    }
}
