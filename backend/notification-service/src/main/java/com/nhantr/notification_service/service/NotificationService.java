package com.nhantr.notification_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.common.api.PageResponse;
import com.nhantr.notification_service.entity.DeviceToken;
import com.nhantr.notification_service.entity.Notification;
import com.nhantr.notification_service.repository.DeviceTokenRepository;
import com.nhantr.notification_service.repository.NotificationRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifications;
    private final DeviceTokenRepository tokens;

    public NotificationService(NotificationRepository notifications, DeviceTokenRepository tokens) {
        this.notifications = notifications;
        this.tokens = tokens;
    }

    @Transactional
    public DeviceTokenResponse register(Long userId, DeviceTokenRequest request) {
        DeviceToken token = tokens.findByToken(request.token())
                .orElseGet(() -> new DeviceToken(userId, request.token(), request.platform()));
        token.assign(userId, request.platform());
        return DeviceTokenResponse.from(tokens.save(token));
    }

    @Transactional
    public void unregister(Long userId, String tokenValue) {
        DeviceToken token = tokens.findByTokenAndUserId(tokenValue, userId)
                .orElseThrow(() -> ApiException.notFound("DEVICE_TOKEN_NOT_FOUND", "Device token not found."));
        token.deactivate();
        tokens.save(token);
    }

    public PageResponse<NotificationResponse> list(Long userId, String cursor, int requestedLimit) {
        Cursor parsed = Cursor.parse(cursor);
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Notification> rows = parsed.time() == null
                ? notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, page)
                : notifications.pageAfter(userId, parsed.time(), parsed.id(), page);
        boolean hasNext = rows.size() > limit;
        List<Notification> selected = hasNext ? rows.subList(0, limit) : rows;
        String next = hasNext && !selected.isEmpty() ? Cursor.of(selected.getLast()).encode() : null;
        return new PageResponse<>(selected.stream().map(NotificationResponse::from).toList(), next, hasNext);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found."));
        notification.markRead();
        return NotificationResponse.from(notifications.save(notification));
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId, Instant.now());
    }

    @Transactional
    public NotificationResponse create(
            Notification.Type type,
            NotificationEvent event,
            String idempotencyKey
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = notifications.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) return NotificationResponse.from(existing.get());
        }
        String title = event.title() == null ? defaultTitle(type) : event.title();
        String body = event.body() == null ? defaultBody(type) : event.body();
        Notification saved = notifications.save(new Notification(
                event.userId(), event.actorUserId(), type, event.referenceId(),
                title, body, idempotencyKey
        ));
        tokens.findByUserIdAndActiveTrue(event.userId()).forEach(token ->
                log.info("Mock push: type={} token={} referenceId={}", type, token.getToken(), event.referenceId())
        );
        return NotificationResponse.from(saved);
    }

    private String defaultTitle(Notification.Type type) {
        return switch (type) {
            case FRIEND_REQUEST -> "New friend request";
            case FRIEND_ACCEPT -> "Friend request accepted";
            case NEW_POST -> "New post from a friend";
            case CHAT_MESSAGE -> "New message";
            case REPORT_RESULT -> "Report updated";
        };
    }

    private String defaultBody(Notification.Type type) {
        return switch (type) {
            case FRIEND_REQUEST -> "Someone wants to connect with you.";
            case FRIEND_ACCEPT -> "Your friend request was accepted.";
            case NEW_POST -> "A friend shared a new post.";
            case CHAT_MESSAGE -> "You received a new chat message.";
            case REPORT_RESULT -> "A moderator completed your report.";
        };
    }

    public record DeviceTokenRequest(
            @NotBlank @Size(max = 4096) String token,
            @NotNull DeviceToken.Platform platform
    ) {}

    public record DeviceTokenResponse(Long id, String token, String platform, boolean active) {
        static DeviceTokenResponse from(DeviceToken token) {
            return new DeviceTokenResponse(token.getId(), token.getToken(), token.getPlatform().name(), token.isActive());
        }
    }

    public record NotificationEvent(
            @NotNull Long userId,
            Long actorUserId,
            Long referenceId,
            @Size(max = 255) String title,
            @Size(max = 2000) String body
    ) {}

    public record NotificationResponse(
            Long id, Long userId, Long actorUserId, String type, Long referenceId,
            String title, String body, boolean read, Instant createdAt, Instant readAt
    ) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(
                    n.getId(), n.getUserId(), n.getActorUserId(), n.getType().name(), n.getReferenceId(),
                    n.getTitle(), n.getBody(), n.isRead(), n.getCreatedAt(), n.getReadAt()
            );
        }
    }

    private record Cursor(Instant time, Long id) {
        static Cursor parse(String value) {
            if (value == null || value.isBlank()) return new Cursor(null, Long.MAX_VALUE);
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(value)).split(":", 2);
                return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
            } catch (RuntimeException exception) {
                throw ApiException.badRequest("CURSOR_INVALID", "Cursor is invalid.");
            }
        }
        static Cursor of(Notification n) { return new Cursor(n.getCreatedAt(), n.getId()); }
        String encode() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString((time.toEpochMilli() + ":" + id).getBytes());
        }
    }
}
