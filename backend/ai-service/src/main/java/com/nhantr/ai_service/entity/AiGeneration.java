package com.nhantr.ai_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "ai_generations")
public class AiGeneration {
    public enum Type { TEXT_TO_IMAGE, IMAGE_TO_IMAGE, FILTER_ENHANCE }
    public enum Status { PENDING, PROCESSING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_media_url")
    private String originalMediaUrl;

    @Column(name = "style_prompt")
    private String stylePrompt;

    @Column(name = "generated_media_url")
    private String generatedMediaUrl;

    @Column(name = "widget_thumbnail_url")
    private String widgetThumbnailUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "generation_type", nullable = false, columnDefinition = "generation_type")
    private Type generationType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "generation_status")
    private Status status = Status.PENDING;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "client_request_id", length = 64)
    private String clientRequestId;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "leased_until")
    private Instant leasedUntil;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "mock_charge_id", length = 64)
    private String mockChargeId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AiGeneration() {}

    public AiGeneration(Long userId, String originalMediaUrl, String stylePrompt, Type generationType) {
        this(userId, originalMediaUrl, stylePrompt, generationType, null, null);
    }

    public AiGeneration(
            Long userId,
            String originalMediaUrl,
            String stylePrompt,
            Type generationType,
            String clientRequestId,
            String mockChargeId
    ) {
        this.userId = userId;
        this.originalMediaUrl = originalMediaUrl;
        this.stylePrompt = stylePrompt;
        this.generationType = generationType;
        this.clientRequestId = clientRequestId;
        this.mockChargeId = mockChargeId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getOriginalMediaUrl() { return originalMediaUrl; }
    public String getStylePrompt() { return stylePrompt; }
    public String getGeneratedMediaUrl() { return generatedMediaUrl; }
    public String getWidgetThumbnailUrl() { return widgetThumbnailUrl; }
    public Type getGenerationType() { return generationType; }
    public Status getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public String getClientRequestId() { return clientRequestId; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeasedUntil() { return leasedUntil; }
    public int getRetryCount() { return retryCount; }
    public String getMockChargeId() { return mockChargeId; }
    public void setMockChargeId(String mockChargeId) { this.mockChargeId = mockChargeId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void lease(String owner, Instant until) {
        this.leaseOwner = owner;
        this.leasedUntil = until;
        this.updatedAt = Instant.now();
    }

    public void processing() {
        this.status = Status.PROCESSING;
        this.updatedAt = Instant.now();
        this.errorMessage = null;
    }

    public void success(String url) {
        success(url, null);
    }

    public void success(String url, String thumbnailUrl) {
        this.status = Status.SUCCESS;
        this.generatedMediaUrl = url;
        this.widgetThumbnailUrl = thumbnailUrl;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
        this.leasedUntil = null;
        this.leaseOwner = null;
    }

    public void fail(String error) {
        this.status = Status.FAILED;
        this.errorMessage = error;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
        this.leasedUntil = null;
        this.leaseOwner = null;
    }

    public void retry() {
        this.status = Status.PENDING;
        this.errorMessage = null;
        this.completedAt = null;
        this.updatedAt = Instant.now();
        this.leasedUntil = null;
        this.leaseOwner = null;
        this.retryCount++;
    }
}
