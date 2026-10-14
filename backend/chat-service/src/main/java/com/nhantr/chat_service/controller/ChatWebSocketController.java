package com.nhantr.chat_service.controller;

import com.nhantr.chat_service.service.ChatService;
import com.nhantr.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
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
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required.");
        }
        service.send(user.id(), request);
    }
}
