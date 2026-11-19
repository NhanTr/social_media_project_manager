package com.nhantr.ai_service.service;

import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.entitlement.SubscriptionEntitlementClient;
import com.nhantr.ai_service.repository.AiGenerationRepository;
import com.nhantr.common.api.ApiException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AiGenerationService {
    private final AiGenerationRepository repository;
    private final AiGenerationWorker worker;
    private final SubscriptionEntitlementClient entitlementClient;

    public AiGenerationService(
            AiGenerationRepository repository,
            AiGenerationWorker worker,
            SubscriptionEntitlementClient entitlementClient
    ) {
        this.repository = repository;
        this.worker = worker;
        this.entitlementClient = entitlementClient;
    }

    @Transactional
    public GenerationResponse create(Long userId, CreateGenerationRequest request, String clientRequestId) {
        String normalizedClientRequestId = normalize(clientRequestId);
        if (normalizedClientRequestId == null) {
            normalizedClientRequestId = normalize(request.clientRequestId());
        }

        // Idempotency: Nếu client gửi trùng clientRequestId, trả về kết quả đã tạo
        if (normalizedClientRequestId != null) {
            var existing = repository.findByUserIdAndClientRequestId(userId, normalizedClientRequestId);
            if (existing.isPresent()) {
                return GenerationResponse.from(existing.get());
            }
        }

        // Entitlement Check: Fail-closed nếu không đủ điều kiện tier
        var decision = entitlementClient.checkAiEntitlement(
                userId,
                request.generationType() != null ? request.generationType().name() : null
        );
        if (!decision.allowed()) {
            throw ApiException.forbidden("AI_TIER_NOT_ENTITLED", decision.reason());
        }

        validate(request);

        AiGeneration generation = new AiGeneration(
                userId,
                normalize(request.originalMediaUrl()),
                request.stylePrompt().trim(),
                request.generationType(),
                normalizedClientRequestId,
                null
        );
        generation = repository.save(generation);

        // Reserve 1 mock charge cho generation lifecycle
        var chargeResult = entitlementClient.reserveCharge(userId, generation.getId());
        if (chargeResult.success()) {
            generation.setMockChargeId(chargeResult.chargeId());
            generation = repository.save(generation);
        }
        worker.processJobAsync(generation.getId());
        return GenerationResponse.from(generation);
    }

    public GenerationResponse get(Long userId, Long generationId) {
        return GenerationResponse.from(owned(userId, generationId));
    }

    @Transactional
    public GenerationResponse retry(Long userId, Long generationId) {
        AiGeneration current = owned(userId, generationId);
        if (current.getStatus() != AiGeneration.Status.FAILED) {
            throw ApiException.conflict("AI_RETRY_NOT_ALLOWED", "Only failed generations can be retried.");
        }

        // Atomic retry update tại DB level để triệt tiêu race condition khi spam retry
        int updatedRows = repository.atomicRetry(generationId, userId, Instant.now());
        if (updatedRows == 0) {
            throw ApiException.conflict("AI_RETRY_CONFLICT", "Job is not in FAILED state or is already being retried.");
        }

        AiGeneration updated = owned(userId, generationId);
        worker.processJobAsync(updated.getId());
        return GenerationResponse.from(updated);
    }

    public VerifyResponse verifyForPublication(Long userId, Long generationId) {
        var opt = repository.findByIdAndUserId(generationId, userId);
        if (opt.isEmpty()) {
            return VerifyResponse.invalid("AI generation not found or does not belong to user.");
        }
        AiGeneration gen = opt.get();
        if (gen.getStatus() != AiGeneration.Status.SUCCESS) {
            return VerifyResponse.invalid("AI generation status is not SUCCESS (current: " + gen.getStatus() + ").");
        }
        return VerifyResponse.valid(
                gen.getGeneratedMediaUrl(),
                gen.getWidgetThumbnailUrl(),
                gen.getOriginalMediaUrl()
        );
    }

    private AiGeneration owned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("AI_GENERATION_NOT_FOUND", "AI generation not found."));
    }

    private void validate(CreateGenerationRequest request) {
        if (request.generationType() != AiGeneration.Type.TEXT_TO_IMAGE
                && (request.originalMediaUrl() == null || request.originalMediaUrl().isBlank())) {
            throw ApiException.badRequest("ORIGINAL_MEDIA_REQUIRED", "This generation type requires originalMediaUrl.");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateGenerationRequest(
            @Size(max = 2048) String originalMediaUrl,
            @NotNull @Size(min = 1, max = 1000) String stylePrompt,
            @NotNull AiGeneration.Type generationType,
            @Size(max = 64) String clientRequestId
    ) {}

    public record GenerationResponse(
            Long id,
            Long userId,
            String originalMediaUrl,
            String stylePrompt,
            String generatedMediaUrl,
            String widgetThumbnailUrl,
            String generationType,
            String status,
            String errorMessage,
            Instant createdAt,
            Instant completedAt,
            int retryCount,
            String mockChargeId
    ) {
        public static GenerationResponse from(AiGeneration generation) {
            return new GenerationResponse(
                    generation.getId(),
                    generation.getUserId(),
                    generation.getOriginalMediaUrl(),
                    generation.getStylePrompt(),
                    generation.getGeneratedMediaUrl(),
                    generation.getWidgetThumbnailUrl(),
                    generation.getGenerationType().name(),
                    generation.getStatus().name(),
                    generation.getErrorMessage(),
                    generation.getCreatedAt(),
                    generation.getCompletedAt(),
                    generation.getRetryCount(),
                    generation.getMockChargeId()
            );
        }
    }

    public record VerifyResponse(
            boolean valid,
            String generatedMediaUrl,
            String widgetThumbnailUrl,
            String originalMediaUrl,
            String errorMessage
    ) {
        public static VerifyResponse valid(String mediaUrl, String widgetThumbnailUrl, String originalMediaUrl) {
            return new VerifyResponse(true, mediaUrl, widgetThumbnailUrl, originalMediaUrl, null);
        }

        public static VerifyResponse invalid(String error) {
            return new VerifyResponse(false, null, null, null, error);
        }
    }
}
