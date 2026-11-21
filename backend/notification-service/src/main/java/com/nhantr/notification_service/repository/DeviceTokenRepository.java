package com.nhantr.notification_service.repository;

import com.nhantr.notification_service.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);
    Optional<DeviceToken> findByTokenAndUserId(String token, Long userId);
    List<DeviceToken> findByUserIdAndActiveTrue(Long userId);

    @Modifying
    @Query("update DeviceToken d set d.active = false where d.userId = :userId and d.active = true")
    int deactivateAllByUserId(@Param("userId") Long userId);
}
