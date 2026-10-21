package com.nhantr.chat_service.service;

import com.nhantr.chat_service.client.NotificationClient;
import com.nhantr.chat_service.client.SocialClient;
import com.nhantr.chat_service.entity.Conversation;
import com.nhantr.chat_service.entity.Message;
import com.nhantr.chat_service.repository.ConversationRepository;
import com.nhantr.chat_service.repository.MessageRepository;
import com.nhantr.chat_service.websocket.PresenceRegistry;
import com.nhantr.common.api.ApiException;
import com.nhantr.common.api.PageResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class ChatService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final SocialClient social;
    private final NotificationClient notifications;
    private final PresenceRegistry presence;
    private final SimpMessagingTemplate messaging;

    public ChatService(
            ConversationRepository conversations,
            MessageRepository messages,
            SocialClient social,
            NotificationClient notifications,
            PresenceRegistry presence,
            SimpMessagingTemplate messaging
    ) {
        this.conversations = conversations;
        this.messages = messages;
        this.social = social;
        this.notifications = notifications;
        this.presence = presence;
        this.messaging = messaging;
    }

    public List<ConversationResponse> list(Long userId) {
        return conversations.findAllForUser(userId).stream().map(ConversationResponse::from).toList();
    }

    @Transactional
    public ConversationResponse open(Long userId, Long friendId) {
        requireChatAllowed(userId, friendId);
        long a = Math.min(userId, friendId);
        long b = Math.max(userId, friendId);
        Conversation conversation = conversations.findByUserId1AndUserId2(a, b)
                .orElseGet(() -> conversations.save(new Conversation(a, b)));
        return ConversationResponse.from(conversation);
    }

    public PageResponse<MessageResponse> history(Long userId, Long conversationId, String cursor, int requestedLimit) {
        Conversation conversation = ownedConversation(userId, conversationId);
        Cursor parsed = Cursor.parse(cursor);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Message> rows = parsed.time() == null
                ? messages.findByConversationIdOrderBySentAtDescIdDesc(conversation.getId(), page)
                : messages.pageAfter(conversation.getId(), parsed.time(), parsed.id(), page);
        boolean hasNext = rows.size() > limit;
        List<Message> selected = hasNext ? rows.subList(0, limit) : rows;
        String next = hasNext && !selected.isEmpty() ? Cursor.of(selected.getLast()).encode() : null;
        return new PageResponse<>(selected.stream().map(MessageResponse::from).toList(), next, hasNext);
    }

    @Transactional
    public MessageResponse send(Long senderId, SendMessageRequest request) {
        Conversation conversation = ownedConversation(senderId, request.conversationId());
        Long receiverId = conversation.other(senderId);
        requireChatAllowed(senderId, receiverId);
        validatePayload(request);
        boolean online = presence.isOnline(receiverId);
        Message message = messages.save(new Message(
                conversation.getId(), senderId, normalize(request.content()), request.mediaUrl(),
                request.thumbnailUrl(), request.messageType(), online
        ));
        conversation.touch();
        conversations.save(conversation);
        MessageResponse response = MessageResponse.from(message);
        messaging.convertAndSend("/topic/conversations/" + conversation.getId(), response);
        if (!online) {
            notifications.chatMessage(receiverId, senderId, message.getId(), preview(request));
        }
        return response;
    }

    @Transactional
    public int markRead(Long userId, Long conversationId) {
        ownedConversation(userId, conversationId);
        return messages.markRead(conversationId, userId, Message.Status.READ, Instant.now());
    }

    private Conversation ownedConversation(Long userId, Long conversationId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation not found."));
        if (!conversation.contains(userId)) {
            throw ApiException.forbidden("CONVERSATION_FORBIDDEN", "User is not a conversation participant.");
        }
        return conversation;
    }

    private void requireChatAllowed(Long a, Long b) {
        if (a == null || b == null || a.equals(b)) {
            throw ApiException.badRequest("INVALID_USER_PAIR", "Two distinct users are required.");
        }
        if (!social.friends(a, b) || social.blocked(a, b)) {
            throw ApiException.forbidden("CHAT_NOT_ALLOWED", "Only unblocked friends can chat.");
        }
    }

    private void validatePayload(SendMessageRequest request) {
        boolean text = request.messageType() == Message.Type.TEXT || request.messageType() == Message.Type.SYSTEM;
        if (text && (request.content() == null || request.content().isBlank())) {
            throw ApiException.badRequest("MESSAGE_CONTENT_REQUIRED", "Text messages require content.");
        }
        if (!text && (request.mediaUrl() == null || request.mediaUrl().isBlank())) {
            throw ApiException.badRequest("MESSAGE_MEDIA_REQUIRED", "Media messages require mediaUrl.");
        }
    }

    private String normalize(String value) { return value == null ? null : value.trim(); }
    private String preview(SendMessageRequest request) {
        if (request.content() == null) return "Sent you media.";
        return request.content().length() > 120 ? request.content().substring(0, 120) : request.content();
    }

    public record SendMessageRequest(
            @NotNull Long conversationId,
            @NotNull Message.Type messageType,
            @Size(max = 5000) String content,
            @Size(max = 2048) String mediaUrl,
            @Size(max = 2048) String thumbnailUrl
    ) {}

    public record ConversationResponse(Long id, Long userId1, Long userId2, Instant lastMessageAt, Instant createdAt) {
        static ConversationResponse from(Conversation c) {
            return new ConversationResponse(c.getId(), c.getUserId1(), c.getUserId2(), c.getLastMessageAt(), c.getCreatedAt());
        }
    }

    public record MessageResponse(
            Long id, Long conversationId, Long senderId, String content, String mediaUrl,
            String thumbnailUrl, String messageType, String status, Instant sentAt,
            Instant deliveredAt, Instant readAt
    ) {
        static MessageResponse from(Message m) {
            return new MessageResponse(
                    m.getId(), m.getConversationId(), m.getSenderId(), m.getContent(), m.getMediaUrl(),
                    m.getThumbnailUrl(), m.getMessageType().name(), m.getStatus().name(), m.getSentAt(),
                    m.getDeliveredAt(), m.getReadAt()
            );
        }
    }

    private record Cursor(Instant time, Long id) {
        static Cursor parse(String value) {
            if (value == null || value.isBlank()) return new Cursor(null, Long.MAX_VALUE);
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(value)).split(":", 2);
                return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
            } catch (RuntimeException exception) {
                throw ApiException.badRequest("CURSOR_INVALID", "Cursor is invalid.");
            }
        }
        static Cursor of(Message m) { return new Cursor(m.getSentAt(), m.getId()); }
        String encode() { return Base64.getUrlEncoder().withoutPadding().encodeToString((time.toEpochMilli() + ":" + id).getBytes()); }
    }
}
