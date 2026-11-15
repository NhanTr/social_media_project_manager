package com.nhantr.ai_service.service;

import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.repository.AiGenerationRepository;
import com.nhantr.common.api.ApiException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.net.URI;
import com.nhantr.ai_service.client.SubscriptionClient;
import jakarta.validation.constraints.NotBlank;

@Service
public class AiGenerationService {
    private final AiGenerationRepository repository;

    private final SubscriptionClient subscriptions;

    public AiGenerationService(AiGenerationRepository repository, SubscriptionClient subscriptions) {
        this.repository = repository;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public GenerationResponse create(Long userId, CreateGenerationRequest request) {
        validate(request);
        subscriptions.requireAi(userId);
        AiGeneration generation = repository.save(new AiGeneration(
                userId, normalize(request.originalMediaUrl()), request.stylePrompt().trim(), request.generationType()
        ));
        return GenerationResponse.from(generation);
    }

    public GenerationResponse get(Long userId, Long generationId) {
        return GenerationResponse.from(owned(userId, generationId));
    }

    @Transactional
    public GenerationResponse retry(Long userId, Long generationId) {
        AiGeneration generation = repository.lockByIdAndUserId(generationId, userId)
                .orElseThrow(() -> ApiException.notFound("AI_GENERATION_NOT_FOUND", "AI generation not found."));
        subscriptions.requireAi(userId);
        if (generation.getStatus() != AiGeneration.Status.FAILED) {
            throw ApiException.conflict("AI_RETRY_NOT_ALLOWED", "Only failed generations can be retried.");
        }
        generation.retry();
        repository.save(generation);
        return GenerationResponse.from(generation);
    }

    private AiGeneration owned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("AI_GENERATION_NOT_FOUND", "AI generation not found."));
    }

    private void validate(CreateGenerationRequest request) {
        if (request == null || request.generationType() == null || request.stylePrompt() == null
                || request.stylePrompt().isBlank() || request.stylePrompt().length() > 1000) {
            throw ApiException.badRequest("AI_INPUT_INVALID", "A generation type and nonblank prompt up to 1000 characters are required.");
        }
        String media = normalize(request.originalMediaUrl());
        if (request.generationType() != AiGeneration.Type.TEXT_TO_IMAGE && media == null) {
            throw ApiException.badRequest("ORIGINAL_MEDIA_REQUIRED", "This generation type requires originalMediaUrl.");
        }
        if (media != null) {
            try {
                URI uri = URI.create(media);
                if (media.length() > 2048 || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getFragment() != null || !("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()))) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                throw ApiException.badRequest("AI_MEDIA_URL_INVALID", "originalMediaUrl must be an absolute HTTP(S) media URL.");
            }
        }
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record CreateGenerationRequest(
            @Size(max = 2048) String originalMediaUrl,
            @NotBlank @Size(min = 1, max = 1000) String stylePrompt,
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
