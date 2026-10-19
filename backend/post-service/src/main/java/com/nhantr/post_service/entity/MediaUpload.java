package com.nhantr.post_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "media_uploads")
public class MediaUpload {
    public enum Status { UPLOADING, FINALIZED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.UPLOADING;

    @Column(name = "media_type", length = 16)
    private String mediaType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    protected MediaUpload() {}

    public MediaUpload(Long userId, String objectKey, Instant expiresAt) {
        this.userId = userId;
        this.objectKey = objectKey;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getObjectKey() { return objectKey; }
    public Status getStatus() { return status; }
    public String getMediaType() { return mediaType; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinalizedAt() { return finalizedAt; }

    public void markFinalized(String mediaType) {
        this.status = Status.FINALIZED;
        this.mediaType = mediaType;
        this.finalizedAt = Instant.now();
    }

    public void markExpired() {
        this.status = Status.EXPIRED;
    }
}