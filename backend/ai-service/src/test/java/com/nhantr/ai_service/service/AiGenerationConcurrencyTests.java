package com.nhantr.ai_service.service;

import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.entitlement.MockSubscriptionEntitlementClient;
import com.nhantr.ai_service.provider.DeterministicMockAiProvider;
import com.nhantr.ai_service.testutil.TestAiRepositories;
import com.nhantr.common.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationConcurrencyTests {
    private TestAiRepositories.FakeAiGenerationRepository repository;
    private MockSubscriptionEntitlementClient entitlementClient;
    private DeterministicMockAiProvider aiProvider;
    private AiGenerationWorker worker;
    private AiGenerationService service;

    @BeforeEach
    void setUp() {
        repository = new TestAiRepositories.FakeAiGenerationRepository();
        entitlementClient = new MockSubscriptionEntitlementClient();
        aiProvider = new DeterministicMockAiProvider();
        worker = new AiGenerationWorker(repository, aiProvider, entitlementClient);
        service = new AiGenerationService(repository, worker, entitlementClient);
    }

    @Test
    void clientRequestIdIsIdempotent() {
        Long userId = 1L;
        String clientReqId = "uuid-client-req-12345";
        var request = new AiGenerationService.CreateGenerationRequest(
                null,
                "Watercolor mountains",
                AiGeneration.Type.TEXT_TO_IMAGE,
                clientReqId
        );

        AiGenerationService.GenerationResponse first = service.create(userId, request, null);
        AiGenerationService.GenerationResponse second = service.create(userId, request, null);

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first.mockChargeId()).isEqualTo(second.mockChargeId());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void concurrentRetrySpamAllowsOnlyOneToSucceed() throws InterruptedException {
        Long userId = 1L;
        var request = new AiGenerationService.CreateGenerationRequest(
                null,
                "Broken neon light [fail]",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-concurrent-001"
        );

        AiGenerationService.GenerationResponse created = service.create(userId, request, null);
        worker.processJob(created.id());

        // Now job is FAILED
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.retry(userId, created.id());
                    successCount.incrementAndGet();
                } catch (ApiException e) {
                    if (e.status().value() == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(e);
                    }
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(unexpectedExceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    void staleWorkerLeaseIsRecoveredByScheduler() {
        Long userId = 1L;
        AiGeneration staleJob = new AiGeneration(
                userId,
                null,
                "Recovered forest landscape",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-stale-001",
                "mock-chg-stale"
        );
        staleJob.lease("crashed-worker-old", Instant.now().minusSeconds(120));
        staleJob.processing();
        repository.save(staleJob);

        // Run recovery
        worker.recoverAndProcessClaimableJobs();

        AiGeneration recovered = repository.findById(staleJob.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(AiGeneration.Status.SUCCESS);
        assertThat(recovered.getGeneratedMediaUrl()).isNotNull();
    }
}
