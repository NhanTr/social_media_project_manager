package com.nhantr.identity_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "user_terms_acceptance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "terms_version_id"})
)
public class UserTermsAcceptance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "terms_version_id", nullable = false)
    private Long termsVersionId;
    @CreationTimestamp
    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    protected UserTermsAcceptance() {}
    public UserTermsAcceptance(Long userId, Long termsVersionId) {
        this.userId = userId;
        this.termsVersionId = termsVersionId;
    }
    public Instant getAcceptedAt() { return acceptedAt; }
}
