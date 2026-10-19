package com.nhantr.chat_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {
    public enum Type { TEXT, IMAGE, GIF, SYSTEM }
    public enum Status { SENT, DELIVERED, READ }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
    @Column(name = "sender_id", nullable = false)
    private Long senderId;
    @Column
    private String content;
    @Column(name = "media_url")
    private String mediaUrl;
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "message_type", nullable = false, columnDefinition = "message_type")
    private Type messageType;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "message_status")
    private Status status;
    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "read_at")
    private Instant readAt;

    protected Message() {}
    public Message(
            Long conversationId, Long senderId, String content, String mediaUrl,
            String thumbnailUrl, Type type, boolean delivered
    ) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.messageType = type;
        this.status = delivered ? Status.DELIVERED : Status.SENT;
        this.deliveredAt = delivered ? Instant.now() : null;
    }
    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getSenderId() { return senderId; }
    public String getContent() { return content; }
    public String getMediaUrl() { return mediaUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Type getMessageType() { return messageType; }
    public Status getStatus() { return status; }
    public Instant getSentAt() { return sentAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
}
