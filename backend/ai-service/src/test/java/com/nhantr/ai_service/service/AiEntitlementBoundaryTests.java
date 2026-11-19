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

class AiEntitlementBoundaryTests {
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
    void validTierAllowsGeneration() {
        Long proUserId = 10L;
        var request = new AiGenerationService.CreateGenerationRequest(
                null,
                "Oil painting of a cozy cafe",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-ent-001"
        );

        AiGenerationService.GenerationResponse resp = service.create(proUserId, request, null);
        assertThat(resp).isNotNull();
        assertThat(resp.status()).isEqualTo("PENDING");
        assertThat(resp.mockChargeId()).isNotNull();
    }

    @Test
    void nonEntitledTierThrowsForbiddenFailClosed() {
        Long freeUserId = 999L; // Denied tier
        var request = new AiGenerationService.CreateGenerationRequest(
                null,
                "Futuristic car in rain",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-ent-002"
        );

        assertThatThrownBy(() -> service.create(freeUserId, request, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(403);
                    assertThat(apiEx.getMessage()).contains("User does not have an active subscription tier");
                });
    }

    @Test
    void mockChargeIsCommittedOnSuccessAndReleasedOnFailure() {
        Long userId = 1L;

        // 1. Success flow
        var successReq = new AiGenerationService.CreateGenerationRequest(
                null,
                "Portrait of an astronaut",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-chg-success"
        );
        var successGen = service.create(userId, successReq, null);
        worker.processJob(successGen.id());
        assertThat(entitlementClient.isChargeActive(successGen.mockChargeId())).isTrue();

        // 2. Failure flow
        var failReq = new AiGenerationService.CreateGenerationRequest(
                null,
                "Landscape with glitch [fail]",
                AiGeneration.Type.TEXT_TO_IMAGE,
                "req-chg-fail"
        );
        var failGen = service.create(userId, failReq, null);
        worker.processJob(failGen.id());
        assertThat(entitlementClient.isChargeActive(failGen.mockChargeId())).isFalse();
    }
}
