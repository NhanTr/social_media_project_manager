package com.nhantr.moderation_service.config;

import com.nhantr.common.security.BearerTokenFilter;
import com.nhantr.common.security.JwtTokenService;
import com.nhantr.common.security.SecuritySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    JwtTokenService jwtTokenService(@Value("${app.jwt.secret}") String secret) {
        return new JwtTokenService(secret);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenService jwt,
            @Value("${app.internal-api-key}") String internalApiKey
    ) throws Exception {
        return SecuritySupport.serviceChain(
                http,
                new BearerTokenFilter(jwt, internalApiKey),
                "/api/v1/admin/auth/login"
        );
    }
}
