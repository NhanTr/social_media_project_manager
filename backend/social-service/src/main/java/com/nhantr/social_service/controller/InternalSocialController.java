package com.nhantr.social_service.controller;

import com.nhantr.social_service.service.SocialService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/v1")
public class InternalSocialController {
    private final SocialService service;

    public InternalSocialController(SocialService service) {
        this.service = service;
    }

    @GetMapping("/friends/{userId}/ids")
    public List<Long> friendIds(@PathVariable Long userId) {
        return service.friendIds(userId);
    }

    @GetMapping("/friends/check")
    public CheckResponse friendship(@RequestParam Long userA, @RequestParam Long userB) {
        return new CheckResponse(service.areFriends(userA, userB));
    }

    @GetMapping("/blocks/check")
    public CheckResponse block(@RequestParam Long userA, @RequestParam Long userB) {
        return new CheckResponse(service.isBlocked(userA, userB));
    }

    public record CheckResponse(boolean value) {}
}
