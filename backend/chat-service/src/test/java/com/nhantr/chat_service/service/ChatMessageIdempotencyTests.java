package com.nhantr.chat_service.service;

import com.nhantr.chat_service.entity.Conversation;
import com.nhantr.chat_service.entity.Message;
import com.nhantr.chat_service.testutil.MockChatClients.MockNotificationClient;
import com.nhantr.chat_service.testutil.MockChatClients.MockSimpMessagingTemplate;
import com.nhantr.chat_service.testutil.MockChatClients.MockSocialClient;
import com.nhantr.chat_service.testutil.TestChatRepositories.FakeConversationRepository;
import com.nhantr.chat_service.testutil.TestChatRepositories.FakeMessageRepository;
import com.nhantr.chat_service.websocket.PresenceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageIdempotencyTests {

    private FakeConversationRepository conversations;
    private FakeMessageRepository messages;
    private MockSocialClient social;
    private MockSimpMessagingTemplate messaging;
    private MockNotificationClient notifications;
    private ChatService service;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversations = new FakeConversationRepository();
        messages = new FakeMessageRepository();
        social = new MockSocialClient();
        messaging = new MockSimpMessagingTemplate();
        notifications = new MockNotificationClient();

        service = new ChatService(
                conversations,
                messages,
                social,
                notifications,
                new PresenceRegistry(),
                messaging
        );

        social.setFriends(100L, 200L, true);
        conversation = conversations.save(new Conversation(100L, 200L));
    }

    @Test
    void reconnectWithSameClientMessageIdReturnsExistingMessageWithoutDuplicateSaveOrBroadcast() {
        String clientMsgId = "locket-client-uuid-999";
        var request = new ChatService.SendMessageRequest(
                conversation.getId(), Message.Type.TEXT, clientMsgId, "Hey, check out this photo!", null, null
        );

        // 1. First send attempt
        var response1 = service.send(100L, request);
        assertThat(response1.id()).isNotNull();
        assertThat(response1.clientMessageId()).isEqualTo(clientMsgId);
        assertThat(messages.count()).isEqualTo(1);
        assertThat(messaging.getSentMessages()).hasSize(1);
        assertThat(notifications.getRecords()).hasSize(1);

        // 2. Simulated network disconnect & reconnect: client resends with identical clientMessageId
        var response2 = service.send(100L, request);

        // Verify identical response returned
        assertThat(response2.id()).isEqualTo(response1.id());
        assertThat(response2.clientMessageId()).isEqualTo(clientMsgId);
        assertThat(response2.content()).isEqualTo("Hey, check out this photo!");

        // Crucial verification: No duplicate in database, no duplicate WebSocket broadcast, no duplicate push!
        assertThat(messages.count()).isEqualTo(1);
        assertThat(messaging.getSentMessages()).hasSize(1);
        assertThat(notifications.getRecords()).hasSize(1);
    }

    @Test
    void distinctClientMessageIdsCreateDistinctMessages() {
        var req1 = new ChatService.SendMessageRequest(
                conversation.getId(), Message.Type.TEXT, "uuid-1", "Photo 1 reply", null, null
        );
        var req2 = new ChatService.SendMessageRequest(
                conversation.getId(), Message.Type.TEXT, "uuid-2", "Photo 2 reply", null, null
        );

        var res1 = service.send(100L, req1);
        var res2 = service.send(100L, req2);

        assertThat(res1.id()).isNotEqualTo(res2.id());
        assertThat(messages.count()).isEqualTo(2);
        assertThat(messaging.getSentMessages()).hasSize(2);
    }
}

