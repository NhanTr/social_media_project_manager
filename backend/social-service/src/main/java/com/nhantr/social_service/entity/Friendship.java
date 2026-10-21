package com.nhantr.social_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "friendships")
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id_a", nullable = false)
    private Long userIdA;
    @Column(name = "user_id_b", nullable = false)
    private Long userIdB;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Friendship() {}
    public Friendship(Long userA, Long userB) {
        this.userIdA = Math.min(userA, userB);
        this.userIdB = Math.max(userA, userB);
    }
    public Long getId() { return id; }
    public Long getUserIdA() { return userIdA; }
    public Long getUserIdB() { return userIdB; }
    public Instant getCreatedAt() { return createdAt; }
    public Long other(Long userId) { return userId.equals(userIdA) ? userIdB : userIdA; }
}
