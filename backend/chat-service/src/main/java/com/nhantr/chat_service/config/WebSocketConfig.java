package com.nhantr.chat_service.config;

import com.nhantr.chat_service.repository.ConversationRepository;
import com.nhantr.common.security.CurrentUser;
import com.nhantr.common.security.JwtTokenService;
import io.jsonwebtoken.JwtException;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtTokenService jwt;
    private final ConversationRepository conversations;

    public WebSocketConfig(JwtTokenService jwt, ConversationRepository conversations) {
        this.jwt = jwt;
        this.conversations = conversations;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeSubscription(accessor);
                }
                return message;
            }
        });
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Bearer token is required.");
        }
        try {
            var claims = jwt.parse(authorization.substring(7));
            if (!"access".equals(claims.get("token_type", String.class))) {
                throw new AccessDeniedException("Access token is required.");
            }
            CurrentUser user = new CurrentUser(
                    Long.valueOf(claims.getSubject()),
                    "USER",
                    Boolean.TRUE.equals(claims.get("terms_accepted", Boolean.class))
            );
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AccessDeniedException("Bearer token is invalid.");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/conversations/")) return;
        if (!(accessor.getUser() instanceof org.springframework.security.core.Authentication authentication)
                || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new AccessDeniedException("Authentication is required.");
        }
        try {
            Long conversationId = Long.valueOf(destination.substring("/topic/conversations/".length()));
            boolean allowed = conversations.findById(conversationId).map(c -> c.contains(user.id())).orElse(false);
            if (!allowed) throw new AccessDeniedException("Conversation subscription is forbidden.");
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Conversation destination is invalid.");
        }
    }
}
