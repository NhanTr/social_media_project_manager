package com.nhantr.chat_service.config;

import com.nhantr.common.security.CurrentUser;
import com.nhantr.common.web.AccountStateClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Mutable state is checked on inbound action and outbound delivery for existing sessions. */
@Component
public class AccountStateChannelInterceptor implements ChannelInterceptor {
    private final AccountStateClient accounts;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    public AccountStateChannelInterceptor(AccountStateClient accounts) { this.accounts = accounts; }
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = StompHeaderAccessor.wrap(message);
        var command = headers.getCommand();
        if (command == StompCommand.DISCONNECT) { if (headers.getSessionId() != null) sessions.remove(headers.getSessionId()); return message; }
        if (command != StompCommand.CONNECT && command != StompCommand.SEND && command != StompCommand.SUBSCRIBE) return message;
        if (!(headers.getUser() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof CurrentUser user) || !active(user.id())) {
            throw new AccessDeniedException("Account is inactive or account state is unavailable.");
        }
        if (command == StompCommand.CONNECT && headers.getSessionId() != null) register(headers.getSessionId(), user.id());
        return message;
    }
    private boolean active(Long id) {
        try { return accounts.isActive(id); }
        catch (RuntimeException exception) { return false; }
    }
    private synchronized void register(String session, Long userId) {
        if (!sessions.containsKey(session) && sessions.size() >= 10_000) throw new AccessDeniedException("Session capacity reached.");
        sessions.put(session, userId);
    }
    public ChannelInterceptor outbound() {
        return new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var headers = StompHeaderAccessor.wrap(message);
                if (headers.getMessageType() != org.springframework.messaging.simp.SimpMessageType.MESSAGE) return message;
                Long id = headers.getSessionId() == null ? null : sessions.get(headers.getSessionId());
                return id != null && active(id) ? message : null;
            }
        };
    }
    @EventListener
    public void disconnected(SessionDisconnectEvent event) { sessions.remove(event.getSessionId()); }
}
