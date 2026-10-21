package com.nhantr.notification_service.controller;

import com.nhantr.common.api.PageResponse;
import com.nhantr.common.security.CurrentUser;
import com.nhantr.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/device-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.DeviceTokenResponse register(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody NotificationService.DeviceTokenRequest request
    ) {
        return service.register(user.id(), request);
    }

    @DeleteMapping("/api/v1/device-tokens/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@AuthenticationPrincipal CurrentUser user, @PathVariable String token) {
        service.unregister(user.id(), token);
    }

    @GetMapping("/api/v1/notifications")
    public PageResponse<NotificationService.NotificationResponse> list(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return service.list(user.id(), cursor, limit);
    }

    @PostMapping("/api/v1/notifications/{id}/read")
    public NotificationService.NotificationResponse read(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id
    ) {
        return service.markRead(user.id(), id);
    }

    @PostMapping("/api/v1/notifications/read-all")
    public ReadAllResponse readAll(@AuthenticationPrincipal CurrentUser user) {
        return new ReadAllResponse(service.markAllRead(user.id()));
    }

    public record ReadAllResponse(int updated) {}
}
