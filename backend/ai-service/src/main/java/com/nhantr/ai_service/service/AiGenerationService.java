package com.nhantr.ai_service.service;

import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.repository.AiGenerationRepository;
import com.nhantr.common.api.ApiException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AiGenerationService {
    private final AiGenerationRepository repository;

    public AiGenerationService(AiGenerationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public GenerationResponse create(Long userId, CreateGenerationRequest request) {
        validate(request);
        AiGeneration generation = repository.save(new AiGeneration(
                userId, normalize(request.originalMediaUrl()), request.stylePrompt().trim(), request.generationType()
        ));
        schedule(generation.getId());
        return GenerationResponse.from(generation);
    }

    public GenerationResponse get(Long userId, Long generationId) {
        return GenerationResponse.from(owned(userId, generationId));
    }

    @Transactional
    public GenerationResponse retry(Long userId, Long generationId) {
        AiGeneration generation = owned(userId, generationId);
        if (generation.getStatus() != AiGeneration.Status.FAILED) {
            throw ApiException.conflict("AI_RETRY_NOT_ALLOWED", "Only failed generations can be retried.");
        }
        generation.retry();
        repository.save(generation);
        schedule(generation.getId());
        return GenerationResponse.from(generation);
    }

    private void schedule(Long id) {
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS).execute(() -> processing(id));
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> complete(id));
    }

    void processing(Long id) {
        repository.findById(id).ifPresent(generation -> {
            if (generation.getStatus() == AiGeneration.Status.PENDING) {
                generation.processing();
                repository.save(generation);
            }
        });
    }

    void complete(Long id) {
        repository.findById(id).ifPresent(generation -> {
            if (generation.getStatus() != AiGeneration.Status.PROCESSING) return;
            if (generation.getStylePrompt().toLowerCase().contains("[fail]")) {
                generation.fail("Mock provider failure requested by prompt.");
            } else {
                String result = generation.getOriginalMediaUrl() == null
                        ? "https://picsum.photos/seed/loccoc-" + id + "/1024/1024"
                        : generation.getOriginalMediaUrl() + (generation.getOriginalMediaUrl().contains("?") ? "&" : "?") + "aiGeneration=" + id;
                generation.success(result);
            }
            repository.save(generation);
        });
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

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record CreateGenerationRequest(
            @Size(max = 2048) String originalMediaUrl,
            @NotNull @Size(min = 1, max = 1000) String stylePrompt,
            @NotNull AiGeneration.Type generationType
    ) {}

    public record GenerationResponse(
            Long id, Long userId, String originalMediaUrl, String stylePrompt, String generatedMediaUrl,
            String generationType, String status, String errorMessage, Instant createdAt, Instant completedAt
    ) {
        static GenerationResponse from(AiGeneration generation) {
            return new GenerationResponse(
                    generation.getId(), generation.getUserId(), generation.getOriginalMediaUrl(),
                    generation.getStylePrompt(), generation.getGeneratedMediaUrl(),
                    generation.getGenerationType().name(), generation.getStatus().name(),
                    generation.getErrorMessage(), generation.getCreatedAt(), generation.getCompletedAt()
            );
        }
    }
}
