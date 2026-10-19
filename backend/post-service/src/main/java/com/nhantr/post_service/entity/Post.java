package com.nhantr.post_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {
    public enum MediaType { IMAGE, GIF }
    public enum Visibility { PUBLIC, FRIENDS_ONLY, PRIVATE }
    public enum Status { ACTIVE, DELETED, HIDDEN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "media_type", nullable = false, columnDefinition = "media_type")
    private MediaType mediaType;
    @Column(name = "media_url", nullable = false)
    private String mediaUrl;
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;
    @Column(name = "media_width")
    private Integer mediaWidth;
    @Column(name = "media_height")
    private Integer mediaHeight;
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
    @Column
    private String caption;
    @Column(name = "filter_applied")
    private String filterApplied;
    @Column(name = "is_ai_generated", nullable = false)
    private boolean aiGenerated;
    @Column(name = "ai_generation_id")
    private Long aiGenerationId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "post_visibility")
    private Visibility visibility = Visibility.FRIENDS_ONLY;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "post_status")
    private Status status = Status.ACTIVE;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Post() {}
    public Post(
            Long userId,
            MediaType mediaType,
            String mediaUrl,
            String thumbnailUrl,
            Integer mediaWidth,
            Integer mediaHeight,
            Long fileSizeBytes,
            Integer durationSeconds,
            String caption,
            String filterApplied,
            boolean aiGenerated,
            Long aiGenerationId,
            Visibility visibility
    ) {
        this.userId = userId;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.mediaWidth = mediaWidth;
        this.mediaHeight = mediaHeight;
        this.fileSizeBytes = fileSizeBytes;
        this.durationSeconds = durationSeconds;
        this.caption = caption;
        this.filterApplied = filterApplied;
        this.aiGenerated = aiGenerated;
        this.aiGenerationId = aiGenerationId;
        this.visibility = visibility == null ? Visibility.FRIENDS_ONLY : visibility;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public MediaType getMediaType() { return mediaType; }
    public String getMediaUrl() { return mediaUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Integer getMediaWidth() { return mediaWidth; }
    public Integer getMediaHeight() { return mediaHeight; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public String getCaption() { return caption; }
    public String getFilterApplied() { return filterApplied; }
    public boolean isAiGenerated() { return aiGenerated; }
    public Long getAiGenerationId() { return aiGenerationId; }
    public Visibility getVisibility() { return visibility; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setStatus(Status status) { this.status = status; this.updatedAt = Instant.now(); }
    public void setCaption(String caption) { this.caption = caption; this.updatedAt = Instant.now(); }
    public void setFilterApplied(String filterApplied) { this.filterApplied = filterApplied; this.updatedAt = Instant.now(); }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; this.updatedAt = Instant.now(); }

    public void markDeleted() {
        this.status = Status.DELETED;
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
