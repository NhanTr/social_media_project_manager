package com.nhantr.chat_service.controller;

import com.nhantr.chat_service.entity.Conversation;
import com.nhantr.chat_service.entity.Message;
import com.nhantr.chat_service.service.ChatService;
import com.nhantr.chat_service.testutil.MockChatClients.MockNotificationClient;
import com.nhantr.chat_service.testutil.MockChatClients.MockSimpMessagingTemplate;
import com.nhantr.chat_service.testutil.MockChatClients.MockSocialClient;
import com.nhantr.chat_service.testutil.TestChatRepositories.FakeConversationRepository;
import com.nhantr.chat_service.testutil.TestChatRepositories.FakeMessageRepository;
import com.nhantr.chat_service.websocket.PresenceRegistry;
import com.nhantr.common.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatControllerTests {

    private FakeConversationRepository conversations;
    private FakeMessageRepository messages;
    private ChatService service;
    private ChatController controller;
    private final CurrentUser user = new CurrentUser(100L, "USER", true);

    @BeforeEach
    void setUp() {
        conversations = new FakeConversationRepository();
        messages = new FakeMessageRepository();
        MockSocialClient social = new MockSocialClient();
        social.setFriends(100L, 200L, true);

        service = new ChatService(
                conversations,
                messages,
                social,
                new MockNotificationClient(),
                new PresenceRegistry(),
                new MockSimpMessagingTemplate()
        );
        controller = new ChatController(service);
    }

    @Test
    void openConversationReturnsConversationResponse() {
        var response = controller.open(user, 200L);
        assertThat(response.id()).isNotNull();
        assertThat(response.userId1()).isEqualTo(100L);
        assertThat(response.userId2()).isEqualTo(200L);
    }

    @Test
    void listConversationsReturnsUserConversations() {
        conversations.save(new Conversation(100L, 200L));
        var list = controller.conversations(user);
        assertThat(list).hasSize(1);
    }

    @Test
    void sendMessageReturnsMessageDetails() {
        Conversation conv = conversations.save(new Conversation(100L, 200L));
        var request = new ChatService.SendMessageRequest(
                conv.getId(), Message.Type.TEXT, "client-id-123", "Hello from REST", null, null
        );

        var msg = controller.send(user, request);
        assertThat(msg.id()).isNotNull();
        assertThat(msg.content()).isEqualTo("Hello from REST");
        assertThat(msg.clientMessageId()).isEqualTo("client-id-123");
    }

    @Test
    void deliveredReceiptEndpointUpdatesMessageStatus() {
        Conversation conv = conversations.save(new Conversation(100L, 200L));
        messages.save(new Message(conv.getId(), 200L, "cid", "Friend message", null, null, Message.Type.TEXT, false));

        var res = controller.delivered(user, conv.getId());
        assertThat(res.updated()).isEqualTo(1);
    }

    @Test
    void readReceiptEndpointUpdatesMessageStatus() {
        Conversation conv = conversations.save(new Conversation(100L, 200L));
        messages.save(new Message(conv.getId(), 200L, "cid", "Friend message", null, null, Message.Type.TEXT, false));

        var res = controller.read(user, conv.getId());
        assertThat(res.updated()).isEqualTo(1);
    }
}

