package com.nhantr.notification_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
    public enum Type { CHAT_MESSAGE, FRIEND_REQUEST, FRIEND_ACCEPT, NEW_POST, REPORT_RESULT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "notification_type")
    private Type type;
    @Column(name = "reference_id")
    private Long referenceId;
    @Column(length = 255)
    private String title;
    @Column
    private String body;
    @Column(name = "is_read", nullable = false)
    private boolean read;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    protected Notification() {}
    public Notification(
            Long userId, Long actorUserId, Type type, Long referenceId,
            String title, String body, String idempotencyKey
    ) {
        this.userId = userId;
        this.actorUserId = actorUserId;
        this.type = type;
        this.referenceId = referenceId;
        this.title = title;
        this.body = body;
        this.idempotencyKey = idempotencyKey;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getActorUserId() { return actorUserId; }
    public Type getType() { return type; }
    public Long getReferenceId() { return referenceId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public void markRead() { this.read = true; this.readAt = Instant.now(); }
}
