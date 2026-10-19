package com.nhantr.social_service.controller;

import com.nhantr.common.security.CurrentUser;
import com.nhantr.social_service.client.IdentityClient;
import com.nhantr.social_service.service.SocialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SocialController {
    private final SocialService service;

    public SocialController(SocialService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/friends/search")
    public List<SocialService.SearchResult> search(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam @Size(min = 1, max = 100) String keyword
    ) {
        return service.search(user.id(), keyword.trim());
    }

    @GetMapping("/api/v1/friends")
    public List<IdentityClient.UserSummary> friends(@AuthenticationPrincipal CurrentUser user) {
        return service.friends(user.id());
    }

    @GetMapping("/api/v1/friends/ids")
    public List<Long> friendIds(@AuthenticationPrincipal CurrentUser user) {
        return service.friendIds(user.id());
    }

    @PostMapping("/api/v1/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public SocialService.RequestResponse send(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody SendRequest request
    ) {
        return service.send(user.id(), request.receiverId());
    }

    @GetMapping("/api/v1/friend-requests/incoming")
    public List<SocialService.RequestResponse> incoming(@AuthenticationPrincipal CurrentUser user) {
        return service.incoming(user.id());
    }

    @GetMapping("/api/v1/friend-requests/outgoing")
    public List<SocialService.RequestResponse> outgoing(@AuthenticationPrincipal CurrentUser user) {
        return service.outgoing(user.id());
    }

    @PostMapping("/api/v1/friend-requests/{id}/accept")
    public SocialService.FriendshipResponse accept(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return service.accept(user.id(), id);
    }

    @PostMapping("/api/v1/friend-requests/{id}/decline")
    public SocialService.RequestResponse decline(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return service.decline(user.id(), id);
    }

    @PostMapping("/api/v1/friend-requests/{id}/cancel")
    public SocialService.RequestResponse cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return service.cancel(user.id(), id);
    }

    @PostMapping("/api/v1/blocks/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SocialService.BlockResponse block(@AuthenticationPrincipal CurrentUser user, @PathVariable Long userId) {
        return service.block(user.id(), userId);
    }

    @DeleteMapping("/api/v1/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal CurrentUser user, @PathVariable Long userId) {
        service.unblock(user.id(), userId);
    }

    @GetMapping("/api/v1/blocks")
    public List<SocialService.BlockResponse> blocks(@AuthenticationPrincipal CurrentUser user) {
        return service.blocks(user.id());
    }

    public record SendRequest(@NotNull Long receiverId) {}
}
