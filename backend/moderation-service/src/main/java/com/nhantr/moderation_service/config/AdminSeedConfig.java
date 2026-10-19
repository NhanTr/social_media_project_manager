package com.nhantr.moderation_service.config;

import com.nhantr.moderation_service.entity.Admin;
import com.nhantr.moderation_service.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeedConfig {
    @Bean
    ApplicationRunner seedAdmin(
            AdminRepository repository,
            PasswordEncoder encoder,
            @Value("${app.admin.username}") String username,
            @Value("${app.admin.password}") String password
    ) {
        return args -> {
            if (!repository.existsByUsernameIgnoreCase(username)) {
                repository.save(new Admin(username, encoder.encode(password), Admin.Role.SUPER_ADMIN));
            }
        };
    }
}
