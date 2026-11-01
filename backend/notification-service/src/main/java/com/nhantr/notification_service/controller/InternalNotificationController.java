package com.nhantr.notification_service.controller;

import com.nhantr.notification_service.email.EmailDeliveryResult;
import com.nhantr.notification_service.email.EmailSendRequest;
import com.nhantr.notification_service.entity.Notification;
import com.nhantr.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
public class InternalNotificationController {
    private final NotificationService service;

    public InternalNotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/notifications/friend-request")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse friendRequest(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.FRIEND_REQUEST, event, key);
    }

    @PostMapping("/notifications/friend-accept")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse friendAccept(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.FRIEND_ACCEPT, event, key);
    }

    @PostMapping("/notifications/new-post")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse newPost(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.NEW_POST, event, key);
    }

    @PostMapping("/notifications/chat-message")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse chat(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.CHAT_MESSAGE, event, key);
    }

    @PostMapping("/notifications/report-result")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse report(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.REPORT_RESULT, event, key);
    }

    @PostMapping("/device-tokens/disable-user/{userId}")
    public DisableUserTokensResponse disableUserTokens(@PathVariable Long userId) {
        int disabledCount = service.disableAllForUser(userId);
        return new DisableUserTokensResponse(userId, disabledCount);
    }

    @PostMapping("/emails/send")
    public EmailDeliveryResult sendEmail(@Valid @RequestBody EmailSendRequest request) {
        return service.sendEmail(request);
    }

    public record DisableUserTokensResponse(Long userId, int disabledTokens) {}
}
