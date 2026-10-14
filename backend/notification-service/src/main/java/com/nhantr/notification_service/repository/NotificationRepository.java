package com.nhantr.notification_service.repository;

import com.nhantr.notification_service.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @Query("""
            select n from Notification n
            where n.userId = :userId
              and (n.createdAt < :cursorTime or (n.createdAt = :cursorTime and n.id < :cursorId))
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> pageAfter(
            @Param("userId") Long userId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = :now where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
