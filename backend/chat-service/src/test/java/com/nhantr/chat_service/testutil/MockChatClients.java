package com.nhantr.chat_service.testutil;

import com.nhantr.chat_service.client.NotificationClient;
import com.nhantr.chat_service.client.SocialClient;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockChatClients {

    public static class MockSocialClient extends SocialClient {
        private final Set<String> friendsPairs = ConcurrentHashMap.newKeySet();
        private final Set<String> blockedPairs = ConcurrentHashMap.newKeySet();

        public MockSocialClient() {
            super();
        }

        public void setFriends(Long u1, Long u2, boolean friends) {
            String pair = pairKey(u1, u2);
            if (friends) friendsPairs.add(pair);
            else friendsPairs.remove(pair);
        }

        public void setBlocked(Long u1, Long u2, boolean blocked) {
            String pair = pairKey(u1, u2);
            if (blocked) blockedPairs.add(pair);
            else blockedPairs.remove(pair);
        }

        @Override
        public boolean friends(Long a, Long b) {
            return friendsPairs.contains(pairKey(a, b));
        }

        @Override
        public boolean blocked(Long a, Long b) {
            return blockedPairs.contains(pairKey(a, b));
        }

        private String pairKey(Long a, Long b) {
            return Math.min(a, b) + ":" + Math.max(a, b);
        }
    }

    public static class MockNotificationClient extends NotificationClient {
        private final List<ChatMessageNotificationRecord> records = new CopyOnWriteArrayList<>();

        public MockNotificationClient() {
            super();
        }

        @Override
        public void chatMessage(Long receiverId, Long senderId, Long messageId, String preview) {
            records.add(new ChatMessageNotificationRecord(receiverId, senderId, messageId, preview));
        }

        public List<ChatMessageNotificationRecord> getRecords() {
            return Collections.unmodifiableList(records);
        }

        public record ChatMessageNotificationRecord(Long receiverId, Long senderId, Long messageId, String preview) {}
    }

    public static class MockSimpMessagingTemplate extends SimpMessagingTemplate {
        private final List<SentMessage> sentMessages = new CopyOnWriteArrayList<>();

        public MockSimpMessagingTemplate() {
            super((message, timeout) -> true);
        }

        @Override
        public void convertAndSend(String destination, Object payload) throws MessagingException {
            sentMessages.add(new SentMessage(destination, payload));
        }

        public List<SentMessage> getSentMessages() {
            return Collections.unmodifiableList(sentMessages);
        }

        public void clear() {
            sentMessages.clear();
        }

        public record SentMessage(String destination, Object payload) {}
    }
}
