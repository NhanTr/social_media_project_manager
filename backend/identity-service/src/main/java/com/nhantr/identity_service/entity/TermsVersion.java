package com.nhantr.identity_service.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "terms_versions")
public class TermsVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String version;
    @Column(name = "content_url", nullable = false)
    private String contentUrl;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    @Column(name = "published_at", nullable = false, insertable = false, updatable = false)
    private Instant publishedAt;

    public Long getId() { return id; }
    public String getVersion() { return version; }
    public String getContentUrl() { return contentUrl; }
    public boolean isActive() { return active; }
    public Instant getPublishedAt() { return publishedAt; }
}
