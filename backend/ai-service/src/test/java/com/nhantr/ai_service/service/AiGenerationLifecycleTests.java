package com.nhantr.ai_service.service;

import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.entitlement.MockSubscriptionEntitlementClient;
import com.nhantr.ai_service.provider.DeterministicMockAiProvider;
import com.nhantr.ai_service.testutil.TestAiRepositories;
import com.nhantr.common.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGenerationLifecycleTests {
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
    void createAndProcessSuccess() {
        Long userId = 1L;
        var request = new AiGenerationService.CreateGenerationRequest(
                null,
                "Cyberpunk cat portrait",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-001"
        );

        AiGenerationService.GenerationResponse created = service.create(userId, request, null);
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.mockChargeId()).isNotNull();

        // Worker synchronously processes job
        boolean processed = worker.processJob(created.id());
        assertThat(processed).isTrue();

        AiGenerationService.GenerationResponse completed = service.get(userId, created.id());
        assertThat(completed.status()).isEqualTo("SUCCESS");
        assertThat(completed.generatedMediaUrl()).contains("seed/loccoc-" + created.id());
        assertThat(completed.widgetThumbnailUrl()).contains("seed/loccoc-" + created.id() + "-widget");
        assertThat(entitlementClient.isChargeActive(completed.mockChargeId())).isTrue();
    }

    @Test
    void createAndProcessFailByPrompt() {
        Long userId = 1L;
        var request = new AiGenerationService.CreateGenerationRequest(
                null,
                "A futuristic city [fail]",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-fail-001"
        );

        AiGenerationService.GenerationResponse created = service.create(userId, request, null);
        assertThat(created.status()).isEqualTo("PENDING");

        worker.processJob(created.id());

        AiGenerationService.GenerationResponse failed = service.get(userId, created.id());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.errorMessage()).contains("Mock provider failure requested by prompt.");
        assertThat(entitlementClient.isChargeActive(failed.mockChargeId())).isFalse();
    }

    @Test
    void retryFailedJobPreservesSingleMockCharge() {
        Long userId = 1L;
        var request = new AiGenerationService.CreateGenerationRequest(
                null,
                "Dragon in the clouds [fail]",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-retry-001"
        );

        AiGenerationService.GenerationResponse created = service.create(userId, request, null);
        worker.processJob(created.id());

        AiGenerationService.GenerationResponse failed = service.get(userId, created.id());
        assertThat(failed.status()).isEqualTo("FAILED");
        String initialChargeId = failed.mockChargeId();

        // Retry the failed job
        AiGenerationService.GenerationResponse retried = service.retry(userId, created.id());
        assertThat(retried.status()).isEqualTo("PENDING");
        assertThat(retried.retryCount()).isEqualTo(1);
        assertThat(retried.mockChargeId()).isEqualTo(initialChargeId);

        // Ensure no new charge was created for this generation
        assertThat(entitlementClient.getChargeCountForGeneration(created.id())).isEqualTo(1);
    }

    @Test
    void retryActiveOrSuccessJobThrowsConflict() {
        Long userId = 1L;
        var request = new AiGenerationService.CreateGenerationRequest(
                null,
                "Sunset over neon ocean",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-conflict-001"
        );

        AiGenerationService.GenerationResponse created = service.create(userId, request, null);
        // While still PENDING, retry is not allowed
        assertThatThrownBy(() -> service.retry(userId, created.id()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only failed generations can be retried");

        // After SUCCESS, retry is still not allowed
        worker.processJob(created.id());
        assertThatThrownBy(() -> service.retry(userId, created.id()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only failed generations can be retried");
    }

    @Test
    void verifyForPublicationChecksOwnershipAndSuccess() {
        Long ownerId = 1L;
        Long otherUserId = 2L;
        var request = new AiGenerationService.CreateGenerationRequest(
                null,
                "Vintage camera on desk",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-pub-001"
        );

        AiGenerationService.GenerationResponse created = service.create(ownerId, request, null);

        // While PENDING, verification fails
        AiGenerationService.VerifyResponse verifyPending = service.verifyForPublication(ownerId, created.id());
        assertThat(verifyPending.valid()).isFalse();

        // After worker finishes with SUCCESS
        worker.processJob(created.id());
        AiGenerationService.VerifyResponse verifySuccess = service.verifyForPublication(ownerId, created.id());
        assertThat(verifySuccess.valid()).isTrue();
        assertThat(verifySuccess.generatedMediaUrl()).isNotNull();
        assertThat(verifySuccess.widgetThumbnailUrl()).isNotNull();

        // If queried with another user ID -> invalid
        AiGenerationService.VerifyResponse verifyOtherUser = service.verifyForPublication(otherUserId, created.id());
        assertThat(verifyOtherUser.valid()).isFalse();
    }
}
