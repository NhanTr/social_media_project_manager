package com.nhantr.chat_service.config;

import com.nhantr.common.security.CurrentUser;
import com.nhantr.common.web.AccountStateClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountStateChannelInterceptorTests {
    @Test
    void brokerMessageWithoutStompCommandIsDroppedAfterBanOrIdentityFailure() {
        var client = mock(AccountStateClient.class);
        when(client.isActive(1L)).thenReturn(true);
        var interceptor = new AccountStateChannelInterceptor(client);
        interceptor.preSend(frame(StompCommand.CONNECT), null);
        var brokerHeaders = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        brokerHeaders.setSessionId("session-1");
        var brokerMessage = MessageBuilder.createMessage(new byte[0], brokerHeaders.getMessageHeaders());
        assertNull(StompHeaderAccessor.wrap(brokerMessage).getCommand());
        assertNotNull(interceptor.outbound().preSend(brokerMessage, null));
        when(client.isActive(1L)).thenReturn(false);
        assertNull(interceptor.outbound().preSend(brokerMessage, null));
        when(client.isActive(1L)).thenThrow(new IllegalStateException("unavailable"));
        assertNull(interceptor.outbound().preSend(brokerMessage, null));
    }

    @Test
    void signedJwtConnectPropagatesPrincipalThenBanDeniesExistingSessionFrame() {
        var client = mock(AccountStateClient.class);
        when(client.isActive(1L)).thenReturn(true);
        var guard = new AccountStateChannelInterceptor(client);
        var jwt = new com.nhantr.common.security.JwtTokenService("test-only-secret-012345678901234567890123456789");
        String token = jwt.create(1L, "access", "USER", java.time.Duration.ofMinutes(5));
        var config = new WebSocketConfig(jwt, mock(com.nhantr.chat_service.repository.ConversationRepository.class), guard);
        var registration = new Registration();
        config.configureClientInboundChannel(registration);
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setSessionId("signed-session"); connect.addNativeHeader("Authorization", "Bearer " + token);
        org.springframework.messaging.Message<?> authenticated = MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders());
        for (var interceptor : registration.configured()) authenticated = interceptor.preSend(authenticated, null);
        var principal = StompHeaderAccessor.wrap(authenticated).getUser();
        assertNotNull(principal);
        when(client.isActive(1L)).thenReturn(false);
        var send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setSessionId("signed-session"); send.setUser(principal); send.setDestination("/app/chat.send");
        var existingSessionFrame = MessageBuilder.createMessage(new byte[0], send.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () -> guard.preSend(existingSessionFrame, null));
        org.junit.jupiter.api.Assertions.assertEquals("1", jwt.parse(token).getSubject());
    }
    private static class Registration extends org.springframework.messaging.simp.config.ChannelRegistration {
        java.util.List<org.springframework.messaging.support.ChannelInterceptor> configured() { return getInterceptors(); }
    }

    @Test
    void bannedExistingSubscriberCannotReceiveFurtherMessages() {
        var client = mock(AccountStateClient.class);
        when(client.isActive(1L)).thenReturn(true);
        var interceptor = new AccountStateChannelInterceptor(client);
        interceptor.preSend(frame(StompCommand.CONNECT), null);
        assertNotNull(interceptor.outbound().preSend(frame(StompCommand.MESSAGE), null));
        when(client.isActive(1L)).thenReturn(false);
        assertNull(interceptor.outbound().preSend(frame(StompCommand.MESSAGE), null));
    }

    @Test
    void accountBannedAfterConnectCannotSendOrSubscribeUsingIssuedSession() {
        var client = mock(AccountStateClient.class);
        when(client.isActive(1L)).thenReturn(true);
        var interceptor = new AccountStateChannelInterceptor(client);
        assertNotNull(interceptor.preSend(frame(StompCommand.CONNECT), null));
        when(client.isActive(1L)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SEND), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.CONNECT), null));
    }
    @Test
    void identityFailureDeniesFrame() {
        var client = mock(AccountStateClient.class);
        when(client.isActive(1L)).thenThrow(new IllegalStateException("unavailable"));
        var interceptor = new AccountStateChannelInterceptor(client);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SEND), null));
    }
    private org.springframework.messaging.Message<byte[]> frame(StompCommand command) {
        var headers = StompHeaderAccessor.create(command);
        headers.setSessionId("session-1");
        headers.setUser(new UsernamePasswordAuthenticationToken(new CurrentUser(1L,"USER",true),null,List.of()));
        return MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders());
    }
}
