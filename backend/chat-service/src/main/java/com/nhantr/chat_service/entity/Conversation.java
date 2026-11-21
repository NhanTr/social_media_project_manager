package com.nhantr.chat_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "conversations")
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id_1", nullable = false)
    private Long userId1;
    @Column(name = "user_id_2", nullable = false)
    private Long userId2;
    @Column(name = "last_message_at")
    private Instant lastMessageAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {}
    public Conversation(Long a, Long b) {
        this.userId1 = Math.min(a, b);
        this.userId2 = Math.max(a, b);
    }
    public Long getId() { return id; }
    public Long getUserId1() { return userId1; }
    public Long getUserId2() { return userId2; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean contains(Long id) { return id.equals(userId1) || id.equals(userId2); }
    public Long other(Long id) { return id.equals(userId1) ? userId2 : userId1; }
    public void touch() { this.lastMessageAt = Instant.now(); }
}
