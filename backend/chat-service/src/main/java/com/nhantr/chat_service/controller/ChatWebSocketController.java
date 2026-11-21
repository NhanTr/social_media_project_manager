package com.nhantr.chat_service.controller;

import com.nhantr.chat_service.service.ChatService;
import com.nhantr.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {
    private final ChatService service;

    public ChatWebSocketController(ChatService service) {
        this.service = service;
    }

    @MessageMapping("/chat.send")
    public void send(@Valid ChatService.SendMessageRequest request, Principal principal) {
        CurrentUser user = extractUser(principal);
        service.send(user.id(), request);
    }

    @MessageMapping("/chat.delivered")
    public void delivered(@Valid ConversationReceiptRequest request, Principal principal) {
        CurrentUser user = extractUser(principal);
        service.markDelivered(user.id(), request.conversationId());
    }

    @MessageMapping("/chat.read")
    public void read(@Valid ConversationReceiptRequest request, Principal principal) {
        CurrentUser user = extractUser(principal);
        service.markRead(user.id(), request.conversationId());
    }

    private CurrentUser extractUser(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new AccessDeniedException("Authentication is required.");
        }
        return user;
    }

    public record ConversationReceiptRequest(@NotNull Long conversationId) {
    }
}
