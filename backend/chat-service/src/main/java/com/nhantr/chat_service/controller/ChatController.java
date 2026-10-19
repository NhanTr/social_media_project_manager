package com.nhantr.chat_service.controller;

import com.nhantr.chat_service.service.ChatService;
import com.nhantr.common.api.PageResponse;
import com.nhantr.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/conversations")
    public List<ChatService.ConversationResponse> conversations(@AuthenticationPrincipal CurrentUser user) {
        return service.list(user.id());
    }

    @PostMapping("/api/v1/conversations/{friendId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatService.ConversationResponse open(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long friendId
    ) {
        return service.open(user.id(), friendId);
    }

    @GetMapping("/api/v1/conversations/{id}/messages")
    public PageResponse<ChatService.MessageResponse> messages(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return service.history(user.id(), id, cursor, limit);
    }

    @PostMapping("/api/v1/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatService.MessageResponse send(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ChatService.SendMessageRequest request
    ) {
        return service.send(user.id(), request);
    }

    @PostMapping("/api/v1/conversations/{id}/read")
    public ReadResponse read(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return new ReadResponse(service.markRead(user.id(), id));
    }

    public record ReadResponse(int updated) {}
}
