package com.nhantr.identity_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "account_deletion_requests")
public class AccountDeletionRequest {
    public enum Status { PENDING, PROCESSING, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "deletion_status")
    private Status status = Status.PENDING;
    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;
    @Column(name = "scheduled_purge_at")
    private Instant scheduledPurgeAt;

    protected AccountDeletionRequest() {}
    public AccountDeletionRequest(Long userId, Instant scheduledPurgeAt) {
        this.userId = userId;
        this.scheduledPurgeAt = scheduledPurgeAt;
    }
    public Long getId() { return id; }
    public Status getStatus() { return status; }
    public Instant getScheduledPurgeAt() { return scheduledPurgeAt; }
}
