package com.nhantr.post_service.config;

import com.nhantr.common.security.BearerTokenFilter;
import com.nhantr.common.security.JwtTokenService;
import com.nhantr.common.security.SecuritySupport;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    JwtTokenService jwtTokenService(@Value("${app.jwt.secret}") String secret) {
        return new JwtTokenService(secret);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenService jwt,
            @Value("${app.internal-api-key}") String internalApiKey
    ) throws Exception {
        return SecuritySupport.serviceChain(http, new BearerTokenFilter(jwt, internalApiKey));
    }

    @Bean
    MinioClient minioClient(
            @Value("${storage.endpoint}") String endpoint,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey
    ) {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }
}
