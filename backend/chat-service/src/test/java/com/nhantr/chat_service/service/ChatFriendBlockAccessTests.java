package com.nhantr.chat_service.service;

import com.nhantr.chat_service.entity.Conversation;
import com.nhantr.chat_service.entity.Message;
import com.nhantr.chat_service.testutil.MockChatClients.MockNotificationClient;
import com.nhantr.chat_service.testutil.MockChatClients.MockSimpMessagingTemplate;
import com.nhantr.chat_service.testutil.MockChatClients.MockSocialClient;
import com.nhantr.chat_service.testutil.TestChatRepositories.FakeConversationRepository;
import com.nhantr.chat_service.testutil.TestChatRepositories.FakeMessageRepository;
import com.nhantr.chat_service.websocket.PresenceRegistry;
import com.nhantr.common.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatFriendBlockAccessTests {

    private FakeConversationRepository conversations;
    private FakeMessageRepository messages;
    private MockSocialClient social;
    private ChatService service;

    @BeforeEach
    void setUp() {
        conversations = new FakeConversationRepository();
        messages = new FakeMessageRepository();
        social = new MockSocialClient();
        service = new ChatService(
                conversations,
                messages,
                social,
                new MockNotificationClient(),
                new PresenceRegistry(),
                new MockSimpMessagingTemplate()
        );
    }

    @Test
    void nonFriendsCannotOpenConversation() {
        social.setFriends(100L, 200L, false);

        assertThatThrownBy(() -> service.open(100L, 200L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.code()).isEqualTo("CHAT_NOT_ALLOWED");
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void blockedPairCannotOpenConversation() {
        social.setFriends(100L, 200L, true);
        social.setBlocked(100L, 200L, true);

        assertThatThrownBy(() -> service.open(100L, 200L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.code()).isEqualTo("CHAT_NOT_ALLOWED");
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void unblockedFriendsCanOpenConversationSuccessfully() {
        social.setFriends(100L, 200L, true);
        social.setBlocked(100L, 200L, false);

        var conv = service.open(100L, 200L);
        assertThat(conv.id()).isNotNull();
        assertThat(conv.userId1()).isEqualTo(100L);
        assertThat(conv.userId2()).isEqualTo(200L);
    }

    @Test
    void nonFriendsCannotSendMessageEvenIfConversationExists() {
        // Conversation was created before friendship was removed
        Conversation conv = conversations.save(new Conversation(100L, 200L));
        social.setFriends(100L, 200L, false);

        var request = new ChatService.SendMessageRequest(
                conv.getId(), Message.Type.TEXT, "client-msg-1", "Hello friend", null, null
        );

        assertThatThrownBy(() -> service.send(100L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.code()).isEqualTo("CHAT_NOT_ALLOWED");
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        assertThat(messages.count()).isEqualTo(0);
    }

    @Test
    void blockedPairCannotSendMessageEvenIfConversationExists() {
        Conversation conv = conversations.save(new Conversation(100L, 200L));
        social.setFriends(100L, 200L, true);
        social.setBlocked(100L, 200L, true); // User A was blocked by User B

        var request = new ChatService.SendMessageRequest(
                conv.getId(), Message.Type.TEXT, "client-msg-2", "Are you there?", null, null
        );

        assertThatThrownBy(() -> service.send(100L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.code()).isEqualTo("CHAT_NOT_ALLOWED");
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        assertThat(messages.count()).isEqualTo(0);
    }
}
