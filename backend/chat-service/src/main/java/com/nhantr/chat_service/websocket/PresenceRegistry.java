package com.nhantr.chat_service.websocket;

import com.nhantr.common.security.CurrentUser;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PresenceRegistry {
    private final Set<Long> online = ConcurrentHashMap.newKeySet();

    @EventListener
    public void connected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getUser() instanceof org.springframework.security.core.Authentication authentication
                && authentication.getPrincipal() instanceof CurrentUser user) {
            online.add(user.id());
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        if (event.getUser() instanceof org.springframework.security.core.Authentication authentication
                && authentication.getPrincipal() instanceof CurrentUser user) {
            online.remove(user.id());
        }
    }

    public boolean isOnline(Long userId) {
        return online.contains(userId);
    }
}
