package com.nhantr.ai_service.service;

import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.entitlement.SubscriptionEntitlementClient;
import com.nhantr.ai_service.provider.AiProvider;
import com.nhantr.ai_service.repository.AiGenerationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AiGenerationWorker {
    private static final Logger log = LoggerFactory.getLogger(AiGenerationWorker.class);
    private static final String WORKER_ID = "worker-" + UUID.randomUUID();

    private final AiGenerationRepository repository;
    private final AiProvider aiProvider;
    private final SubscriptionEntitlementClient entitlementClient;

    public AiGenerationWorker(
            AiGenerationRepository repository,
            AiProvider aiProvider,
            SubscriptionEntitlementClient entitlementClient
    ) {
        this.repository = repository;
        this.aiProvider = aiProvider;
        this.entitlementClient = entitlementClient;
    }

    public void processJobAsync(Long generationId) {
        CompletableFuture.runAsync(() -> {
            try {
                processJob(generationId);
            } catch (Exception e) {
                log.error("Failed to process AI generation job {}", generationId, e);
            }
        });
    }

    @Transactional
    public boolean processJob(Long generationId) {
        return repository.findById(generationId).map(this::executeJob).orElse(false);
    }

    private boolean executeJob(AiGeneration generation) {
        if (generation.getStatus() != AiGeneration.Status.PENDING
                && generation.getStatus() != AiGeneration.Status.PROCESSING) {
            return false;
        }

        // Đặt lease cho worker để tránh các worker khác nhặt trùng
        generation.lease(WORKER_ID, Instant.now().plusSeconds(60));
        generation.processing();
        repository.saveAndFlush(generation);

        AiProvider.AiGenerationContext context = new AiProvider.AiGenerationContext(
                generation.getId(),
                generation.getUserId(),
                generation.getStylePrompt(),
                generation.getOriginalMediaUrl(),
                generation.getGenerationType().name()
        );

        AiProvider.AiGenerationResult result = aiProvider.generate(context);

        if (result.success()) {
            generation.success(result.generatedMediaUrl(), result.widgetThumbnailUrl());
            repository.save(generation);
            entitlementClient.commitCharge(generation.getMockChargeId());
            return true;
        } else {
            generation.fail(result.errorMessage());
            repository.save(generation);
            entitlementClient.releaseCharge(generation.getMockChargeId());
            return false;
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void recoverAndProcessClaimableJobs() {
        List<AiGeneration> claimable = repository.findClaimableJobs(Instant.now(), PageRequest.of(0, 10));
        for (AiGeneration job : claimable) {
            try {
                executeJob(job);
            } catch (Exception e) {
                log.warn("Recovery execution failed for job {}", job.getId(), e);
            }
        }
    }
}
