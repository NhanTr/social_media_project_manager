package com.nhantr.identity_service.config;

import com.nhantr.identity_service.entity.User;
import com.nhantr.identity_service.enums.AuthProvider;
import com.nhantr.identity_service.enums.UserStatus;
import com.nhantr.identity_service.repository.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "app.seed.test-user",
        name = "enabled",
        havingValue = "true"
)
public class InitUserTestConfig {

    @Bean
    ApplicationRunner initTestUser(UserRepository userRepository) {
        return args -> {
            String phoneNumber = "+84900000000";

            if (userRepository.existsByPhoneNumber(phoneNumber)) {
                return;
            }

            User user = new User();
            user.setUsername("test_user");
            user.setPhoneNumber(phoneNumber);
            user.setAuthProvider(AuthProvider.PHONE);
            user.setDisplayName("Test User");
            user.setStatus(UserStatus.ACTIVE);
            user.setFriendInviteCode("TESTUSER01");

            userRepository.save(user);
        };
    }
}
