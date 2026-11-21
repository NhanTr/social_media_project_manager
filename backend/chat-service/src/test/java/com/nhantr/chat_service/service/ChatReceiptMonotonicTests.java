package com.nhantr.chat_service.service;

import com.nhantr.chat_service.entity.Conversation;
import com.nhantr.chat_service.entity.Message;
import com.nhantr.chat_service.testutil.MockChatClients.MockNotificationClient;
import com.nhantr.chat_service.testutil.MockChatClients.MockSimpMessagingTemplate;
import com.nhantr.chat_service.testutil.MockChatClients.MockSocialClient;
import com.nhantr.chat_service.testutil.TestChatRepositories.FakeConversationRepository;
import com.nhantr.chat_service.testutil.TestChatRepositories.FakeMessageRepository;
import com.nhantr.chat_service.websocket.ChatReceiptEvent;
import com.nhantr.chat_service.websocket.PresenceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChatReceiptMonotonicTests {

    private FakeConversationRepository conversations;
    private FakeMessageRepository messages;
    private MockSimpMessagingTemplate messaging;
    private ChatService service;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversations = new FakeConversationRepository();
        messages = new FakeMessageRepository();
        MockSocialClient social = new MockSocialClient();
        messaging = new MockSimpMessagingTemplate();

        service = new ChatService(
                conversations,
                messages,
                social,
                new MockNotificationClient(),
                new PresenceRegistry(),
                messaging
        );

        social.setFriends(100L, 200L, true);
        conversation = conversations.save(new Conversation(100L, 200L));
    }

    @Test
    void statusProgressesMonotonicallyFromSentToDeliveredToRead() {
        // 1. User 100 sends a message while User 200 is offline -> status = SENT
        var sendRequest = new ChatService.SendMessageRequest(
                conversation.getId(), Message.Type.TEXT, "cid-1", "Hello friend!", null, null
        );
        var msg = service.send(100L, sendRequest);
        assertThat(msg.status()).isEqualTo("SENT");
        assertThat(msg.deliveredAt()).isNull();
        assertThat(msg.readAt()).isNull();

        messaging.clear();

        // 2. User 200 comes online / device acknowledges delivery -> status = DELIVERED
        int deliveredCount = service.markDelivered(200L, conversation.getId());
        assertThat(deliveredCount).isEqualTo(1);

        Message inDb = messages.findById(msg.id()).get();
        assertThat(inDb.getStatus()).isEqualTo(Message.Status.DELIVERED);
        assertThat(inDb.getDeliveredAt()).isNotNull();
        assertThat(inDb.getReadAt()).isNull();

        // Verify WebSocket receipt event broadcasted
        assertThat(messaging.getSentMessages()).hasSize(1);
        ChatReceiptEvent deliveredEvent = (ChatReceiptEvent) messaging.getSentMessages().get(0).payload();
        assertThat(deliveredEvent.receiptType()).isEqualTo("DELIVERED");
        assertThat(deliveredEvent.conversationId()).isEqualTo(conversation.getId());
        assertThat(deliveredEvent.participantId()).isEqualTo(200L);

        messaging.clear();

        // 3. User 200 opens the chat screen -> status = READ
        int readCount = service.markRead(200L, conversation.getId());
        assertThat(readCount).isEqualTo(1);

        inDb = messages.findById(msg.id()).get();
        assertThat(inDb.getStatus()).isEqualTo(Message.Status.READ);
        assertThat(inDb.getReadAt()).isNotNull();
        assertThat(inDb.getDeliveredAt()).isNotNull();

        // Verify WebSocket receipt event broadcasted
        assertThat(messaging.getSentMessages()).hasSize(1);
        ChatReceiptEvent readEvent = (ChatReceiptEvent) messaging.getSentMessages().get(0).payload();
        assertThat(readEvent.receiptType()).isEqualTo("READ");
    }

    @Test
    void receiptNeverRegressesFromReadBackToDeliveredOrSent() {
        // Message sent and immediately read
        Message message = new Message(
                conversation.getId(), 100L, "cid-2", "Text", null, null, Message.Type.TEXT, false
        );
        Instant now = Instant.now();
        message.markRead(now);
        messages.save(message);

        assertThat(message.getStatus()).isEqualTo(Message.Status.READ);
        Instant originalReadAt = message.getReadAt();

        // A late arriving delivery ack attempts to update the message
        boolean deliveredUpdated = message.markDelivered(now.plusSeconds(10));

        // Must reject regression
        assertThat(deliveredUpdated).isFalse();
        assertThat(message.getStatus()).isEqualTo(Message.Status.READ);
        assertThat(message.getReadAt()).isEqualTo(originalReadAt);

        // Calling markDelivered via service also leaves READ messages intact
        int updated = service.markDelivered(200L, conversation.getId());
        assertThat(updated).isEqualTo(0);
        assertThat(messages.findById(message.getId()).get().getStatus()).isEqualTo(Message.Status.READ);
    }

    @Test
    void markReadDirectlyFromSentSetsBothDeliveredAtAndReadAt() {
        Message message = messages.save(new Message(
                conversation.getId(), 100L, "cid-3", "Quick read", null, null, Message.Type.TEXT, false
        ));
        assertThat(message.getStatus()).isEqualTo(Message.Status.SENT);
        assertThat(message.getDeliveredAt()).isNull();

        service.markRead(200L, conversation.getId());

        Message updated = messages.findById(message.getId()).get();
        assertThat(updated.getStatus()).isEqualTo(Message.Status.READ);
        // If read directly, deliveredAt must also be non-null and <= readAt
        assertThat(updated.getDeliveredAt()).isNotNull();
        assertThat(updated.getReadAt()).isNotNull();
        assertThat(updated.getDeliveredAt()).isBeforeOrEqualTo(updated.getReadAt());
    }
}

