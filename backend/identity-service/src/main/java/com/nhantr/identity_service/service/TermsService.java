package com.nhantr.identity_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.identity_service.entity.TermsVersion;
import com.nhantr.identity_service.entity.UserTermsAcceptance;
import com.nhantr.identity_service.repository.TermsVersionRepository;
import com.nhantr.identity_service.repository.UserRepository;
import com.nhantr.identity_service.repository.UserTermsAcceptanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TermsService {
    private final TermsVersionRepository termsRepository;
    private final UserTermsAcceptanceRepository acceptanceRepository;
    private final UserRepository userRepository;
    private final AuthAuditLogger auditLogger;

    public TermsService(
            TermsVersionRepository termsRepository,
            UserTermsAcceptanceRepository acceptanceRepository,
            UserRepository userRepository,
            AuthAuditLogger auditLogger
    ) {
        this.termsRepository = termsRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    public TermsResponse latest() {
        TermsVersion terms = latestEntity();
        return new TermsResponse(
                terms.getId(),
                terms.getVersion(),
                terms.getContentUrl(),
                terms.getPublishedAt()
        );
    }

    public boolean hasAcceptedLatest(Long userId) {
        TermsVersion terms = latestEntity();
        return acceptanceRepository.existsByUserIdAndTermsVersionId(userId, terms.getId());
    }

    @Transactional
    public AcceptanceResponse accept(Long userId, Long termsVersionId) {
        if (!userRepository.existsById(userId)) {
            throw ApiException.notFound("USER_NOT_FOUND", "User not found.");
        }
        TermsVersion latest = latestEntity();
        if (!latest.getId().equals(termsVersionId)) {
            throw ApiException.badRequest("TERMS_VERSION_OUTDATED", "The latest terms version must be accepted.");
        }
        if (!acceptanceRepository.existsByUserIdAndTermsVersionId(userId, termsVersionId)) {
            acceptanceRepository.save(new UserTermsAcceptance(userId, termsVersionId));
        }
        audit("terms_acceptance", "succeeded", userId);
        return new AcceptanceResponse(termsVersionId, Instant.now());
    }

    private TermsVersion latestEntity() {
        return termsRepository.findFirstByActiveTrueOrderByPublishedAtDesc()
                .orElseThrow(() -> ApiException.notFound("TERMS_NOT_FOUND", "No active terms version exists."));
    }

    private void audit(String event, String outcome, Long userId) {
        try {
            auditLogger.record(event, outcome, userId);
        } catch (RuntimeException ignored) {
            // Audit failures must never change Terms acceptance outcomes.
        }
    }

    public record TermsResponse(Long id, String version, String contentUrl, Instant publishedAt) {}
    public record AcceptanceResponse(Long termsVersionId, Instant acceptedAt) {}
}
