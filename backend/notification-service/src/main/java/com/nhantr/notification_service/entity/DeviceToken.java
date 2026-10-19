package com.nhantr.notification_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "device_tokens")
public class DeviceToken {
    public enum Platform { IOS, ANDROID, WEB }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "fcm_token", nullable = false, unique = true)
    private String token;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "platform_type")
    private Platform platform;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected DeviceToken() {}
    public DeviceToken(Long userId, String token, Platform platform) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getToken() { return token; }
    public Platform getPlatform() { return platform; }
    public boolean isActive() { return active; }
    public void assign(Long userId, Platform platform) {
        this.userId = userId; this.platform = platform; this.active = true; this.updatedAt = Instant.now();
    }
    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
}
