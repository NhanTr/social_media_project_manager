package com.nhantr.notification_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.common.api.PageResponse;
import com.nhantr.notification_service.delivery.NotificationDeliveryService;
import com.nhantr.notification_service.email.EmailDeliveryResult;
import com.nhantr.notification_service.email.EmailSendRequest;
import com.nhantr.notification_service.email.MockEmailAdapter;
import com.nhantr.notification_service.entity.DeviceToken;
import com.nhantr.notification_service.entity.Notification;
import com.nhantr.notification_service.provider.PushDeliveryResult;
import com.nhantr.notification_service.provider.PushNotificationPayload;
import com.nhantr.notification_service.provider.PushNotificationProvider;
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
    private final PushNotificationProvider pushProvider;
    private final MockEmailAdapter emailAdapter;
    private final NotificationDeliveryService deliveryService;

    public NotificationService(
            NotificationRepository notifications,
            DeviceTokenRepository tokens,
            PushNotificationProvider pushProvider,
            MockEmailAdapter emailAdapter,
            NotificationDeliveryService deliveryService
    ) {
        this.notifications = notifications;
        this.tokens = tokens;
        this.pushProvider = pushProvider;
        this.emailAdapter = emailAdapter;
        this.deliveryService = deliveryService;
    }

    @Transactional
    public DeviceTokenResponse register(Long userId, DeviceTokenRequest request) {
        // Rotate: if oldToken is provided and different, deactivate the old token
        if (request.oldToken() != null && !request.oldToken().isBlank()
                && !request.oldToken().equals(request.token())) {
            tokens.findByToken(request.oldToken()).ifPresent(old -> {
                old.deactivate();
                tokens.save(old);
                log.info("Token rotated: deactivated old token for userId={}", userId);
            });
        }

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

    @Transactional
    public int disableAllForUser(Long userId) {
        List<DeviceToken> activeTokens = tokens.findByUserIdAndActiveTrue(userId);
        for (DeviceToken token : activeTokens) {
            token.deactivate();
            tokens.save(token);
        }
        log.info("Disabled {} active device tokens for logout userId={}", activeTokens.size(), userId);
        return activeTokens.size();
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

    public long getUnreadCount(Long userId) {
        return notifications.countByUserIdAndReadFalse(userId);
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
            if (existing.isPresent()) {
                log.info("Idempotent notification hit for key={}", idempotencyKey);
                return NotificationResponse.from(existing.get());
            }
        }
        String title = event.title() == null ? defaultTitle(type) : event.title();
        String body = event.body() == null ? defaultBody(type) : event.body();
        Notification saved = notifications.save(new Notification(
                event.userId(), event.actorUserId(), type, event.referenceId(),
                title, body, idempotencyKey
        ));

        // Push to active device tokens
        List<DeviceToken> activeTokens = tokens.findByUserIdAndActiveTrue(event.userId());
        for (DeviceToken token : activeTokens) {
            PushNotificationPayload payload = PushNotificationPayload.of(
                    token.getToken(), title, body, type.name(), event.referenceId()
            );
            PushDeliveryResult result = pushProvider.send(payload);
            if (!result.success() && result.retryable() && deliveryService != null) {
                var delivery = deliveryService.schedulePushDelivery(saved.getId(), token.getToken(), 3);
                deliveryService.processDeliveryAttempt(delivery, payload);
            }
        }

        return NotificationResponse.from(saved);
    }

    public EmailDeliveryResult sendEmail(EmailSendRequest request) {
        return emailAdapter.send(request);
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
            @NotNull DeviceToken.Platform platform,
            @Size(max = 4096) String oldToken
    ) {
        public DeviceTokenRequest(String token, DeviceToken.Platform platform) {
            this(token, platform, null);
        }
    }

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
