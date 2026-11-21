package com.nhantr.social_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "blocks")
public class Block {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;
    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Block() {}
    public Block(Long blockerId, Long blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }
    public Long getId() { return id; }
    public Long getBlockerId() { return blockerId; }
    public Long getBlockedId() { return blockedId; }
    public Instant getCreatedAt() { return createdAt; }
}
