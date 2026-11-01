package com.nhantr.notification_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {
    public enum Channel { PUSH, EMAIL }
    public enum Status { PENDING, SENT, FAILED, EXHAUSTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id")
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Column(nullable = false, length = 500)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationDelivery() {}

    public NotificationDelivery(Long notificationId, Channel channel, String recipient, int maxAttempts) {
        this.notificationId = notificationId;
        this.channel = channel;
        this.recipient = recipient;
        this.maxAttempts = maxAttempts;
        this.status = Status.PENDING;
        this.scheduledAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getNotificationId() { return notificationId; }
    public Channel getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getLastError() { return lastError; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markSuccess() {
        this.status = Status.SENT;
        this.deliveredAt = Instant.now();
    }

    public void recordFailure(String error, Instant nextScheduledAt) {
        this.attempts++;
        this.lastError = error;
        if (this.attempts >= this.maxAttempts) {
            this.status = Status.EXHAUSTED;
        } else {
            this.status = Status.FAILED;
            this.scheduledAt = nextScheduledAt;
        }
    }
}

