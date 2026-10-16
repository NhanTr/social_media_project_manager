package com.nhantr.social_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "friend_requests")
public class FriendRequest {
    public enum Status { PENDING, ACCEPTED, DECLINED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sender_id", nullable = false)
    private Long senderId;
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "request_status")
    private Status status = Status.PENDING;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected FriendRequest() {}
    public FriendRequest(Long senderId, Long receiverId) {
        this.senderId = senderId;
        this.receiverId = receiverId;
    }
    public Long getId() { return id; }
    public Long getSenderId() { return senderId; }
    public Long getReceiverId() { return receiverId; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(Status status) { this.status = status; this.updatedAt = Instant.now(); }
}
