package com.nhantr.notification_service.controller;

import com.nhantr.notification_service.entity.Notification;
import com.nhantr.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/notifications")
public class InternalNotificationController {
    private final NotificationService service;

    public InternalNotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/friend-request")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse friendRequest(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.FRIEND_REQUEST, event, key);
    }

    @PostMapping("/friend-accept")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse friendAccept(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.FRIEND_ACCEPT, event, key);
    }

    @PostMapping("/new-post")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse newPost(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.NEW_POST, event, key);
    }

    @PostMapping("/chat-message")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse chat(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.CHAT_MESSAGE, event, key);
    }

    @PostMapping("/report-result")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.NotificationResponse report(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody NotificationService.NotificationEvent event
    ) {
        return service.create(Notification.Type.REPORT_RESULT, event, key);
    }
}
