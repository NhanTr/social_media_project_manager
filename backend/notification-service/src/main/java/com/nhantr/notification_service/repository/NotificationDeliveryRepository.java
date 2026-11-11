package com.nhantr.notification_service.repository;

import com.nhantr.notification_service.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    List<NotificationDelivery> findByStatusAndScheduledAtBefore(NotificationDelivery.Status status, Instant scheduledAt);
    List<NotificationDelivery> findByNotificationId(Long notificationId);
}

