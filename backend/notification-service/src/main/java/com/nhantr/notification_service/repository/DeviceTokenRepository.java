package com.nhantr.notification_service.repository;

import com.nhantr.notification_service.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);
    Optional<DeviceToken> findByTokenAndUserId(String token, Long userId);
    List<DeviceToken> findByUserIdAndActiveTrue(Long userId);
}
