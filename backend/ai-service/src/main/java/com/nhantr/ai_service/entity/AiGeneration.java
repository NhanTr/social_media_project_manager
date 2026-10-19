package com.nhantr.ai_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
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
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected AiGeneration() {}
    public AiGeneration(Long userId, String originalMediaUrl, String stylePrompt, Type generationType) {
        this.userId = userId;
        this.originalMediaUrl = originalMediaUrl;
        this.stylePrompt = stylePrompt;
        this.generationType = generationType;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getOriginalMediaUrl() { return originalMediaUrl; }
    public String getStylePrompt() { return stylePrompt; }
    public String getGeneratedMediaUrl() { return generatedMediaUrl; }
    public Type getGenerationType() { return generationType; }
    public Status getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void processing() { status = Status.PROCESSING; updatedAt = Instant.now(); errorMessage = null; }
    public void success(String url) { status = Status.SUCCESS; generatedMediaUrl = url; completedAt = Instant.now(); updatedAt = completedAt; }
    public void fail(String error) { status = Status.FAILED; errorMessage = error; completedAt = Instant.now(); updatedAt = completedAt; }
    public void retry() { status = Status.PENDING; errorMessage = null; completedAt = null; updatedAt = Instant.now(); }
}
