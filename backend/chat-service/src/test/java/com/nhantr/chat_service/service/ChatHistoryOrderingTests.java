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

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChatHistoryOrderingTests {

    private FakeConversationRepository conversations;
    private FakeMessageRepository messages;
    private ChatService service;
    private Conversation conversation;

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

        conversation = conversations.save(new Conversation(100L, 200L));
    }

    @Test
    void messagesAreReturnedInStrictDescendingServerOrder() throws Exception {
        Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");

        // Create 3 messages with staggered server timestamps
        Message m1 = createMessageWithTime(conversation.getId(), 100L, "First photo", baseTime);
        Message m2 = createMessageWithTime(conversation.getId(), 200L, "Second reply", baseTime.plusSeconds(60));
        Message m3 = createMessageWithTime(conversation.getId(), 100L, "Third reaction", baseTime.plusSeconds(120));

        var page = service.history(100L, conversation.getId(), null, 10);

        assertThat(page.items()).hasSize(3);
        // Most recent message (Third reaction) must come first
        assertThat(page.items().get(0).content()).isEqualTo("Third reaction");
        assertThat(page.items().get(1).content()).isEqualTo("Second reply");
        assertThat(page.items().get(2).content()).isEqualTo("First photo");
    }

    @Test
    void cursorPaginationRetrievesSubsequentPagesWithoutGapsOrDuplication() throws Exception {
        Instant baseTime = Instant.parse("2026-11-01T12:00:00Z");

        for (int i = 1; i <= 5; i++) {
            createMessageWithTime(conversation.getId(), 100L, "Message " + i, baseTime.plusSeconds(i * 10));
        }

        // Page 1: limit 2
        var page1 = service.history(100L, conversation.getId(), null, 2);
        assertThat(page1.items()).hasSize(2);
        assertThat(page1.items().get(0).content()).isEqualTo("Message 5");
        assertThat(page1.items().get(1).content()).isEqualTo("Message 4");
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();

        // Page 2: with cursor from page 1
        var page2 = service.history(100L, conversation.getId(), page1.nextCursor(), 2);
        assertThat(page2.items()).hasSize(2);
        assertThat(page2.items().get(0).content()).isEqualTo("Message 3");
        assertThat(page2.items().get(1).content()).isEqualTo("Message 2");
        assertThat(page2.hasNext()).isTrue();

        // Page 3: with cursor from page 2
        var page3 = service.history(100L, conversation.getId(), page2.nextCursor(), 2);
        assertThat(page3.items()).hasSize(1);
        assertThat(page3.items().get(0).content()).isEqualTo("Message 1");
        assertThat(page3.hasNext()).isFalse();
    }

    private Message createMessageWithTime(Long convId, Long senderId, String content, Instant time) throws Exception {
        Message message = new Message(convId, senderId, content, null, null, Message.Type.TEXT, false);
        messages.save(message);

        Field sentField = Message.class.getDeclaredField("sentAt");
        sentField.setAccessible(true);
        sentField.set(message, time);

        return messages.save(message);
    }
}

