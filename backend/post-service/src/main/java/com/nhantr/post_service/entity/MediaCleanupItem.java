package com.nhantr.post_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "media_cleanup_queue")
public class MediaCleanupItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "done_at")
    private Instant doneAt;

    protected MediaCleanupItem() {}

    public MediaCleanupItem(String objectKey) {
        this(objectKey, Instant.now());
    }

    public MediaCleanupItem(String objectKey, Instant scheduledAt) {
        this.objectKey = objectKey;
        this.scheduledAt = scheduledAt;
        this.attempts = 0;
    }

    public Long getId() { return id; }
    public String getObjectKey() { return objectKey; }
    public Instant getScheduledAt() { return scheduledAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getDoneAt() { return doneAt; }

    public void defer() { this.scheduledAt = Instant.now().plusSeconds(300); }

    public void markDone() {
        this.doneAt = Instant.now();
    }

    public void recordFailure(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (this.attempts < maxAttempts) {
            this.scheduledAt = Instant.now().plusSeconds(300L * this.attempts);
        }
    }

    public boolean isExhausted(int maxAttempts) {
        return this.attempts >= maxAttempts;
    }
}