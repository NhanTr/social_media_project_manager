package com.nhantr.ai_service.service;

import com.nhantr.ai_service.client.SubscriptionClient;
import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.repository.AiGenerationRepository;
import com.nhantr.common.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiGenerationServiceTests {
    private final AiGenerationRepository repository = mock(AiGenerationRepository.class);
    private final SubscriptionClient subscriptions = mock(SubscriptionClient.class);
    private AiGenerationService service;
    @BeforeEach void setUp() { service = new AiGenerationService(repository, subscriptions); }

    @Test void refusesCreateWithoutAnActiveGrant() {
        doThrow(ApiException.forbidden("AI_TIER_REQUIRED", "Denied")).when(subscriptions).requireAi(7L);
        assertThatThrownBy(() -> service.create(7L, request("https://media.test/original.png", "anime")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("AI_TIER_REQUIRED");
        verifyNoInteractions(repository);
    }
    @Test void validatesBlankPromptAndUnsafeOriginalBeforePersisting() {
        for (var request : new AiGenerationService.CreateGenerationRequest[]{
                request("https://media.test/a", "   "), request("file:///etc/passwd", "anime"),
                request("https://user:pass@media.test/a", "anime"), request(null, "anime")}) {
            assertThatThrownBy(() -> service.create(7L, request)).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(repository);
    }
    @Test void createsPendingJobWithoutStartingProviderInsideTransaction() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.create(7L, request(" https://media.test/a ", " anime "));
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.originalMediaUrl()).isEqualTo("https://media.test/a");
        assertThat(result.stylePrompt()).isEqualTo("anime");
    }
    @Test void anotherOwnerCannotReadOrRetry() {
        when(repository.findByIdAndUserId(8L, 7L)).thenReturn(Optional.empty());
        when(repository.lockByIdAndUserId(8L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(7L, 8L)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("AI_GENERATION_NOT_FOUND");
        assertThatThrownBy(() -> service.retry(7L, 8L)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("AI_GENERATION_NOT_FOUND");
    }
    @Test void retryRequiresFreshGrantAndRetainsSingleMockCharge() {
        AiGeneration job = new AiGeneration(7L, "https://media.test/a", "[fail]", AiGeneration.Type.IMAGE_TO_IMAGE);
        job.claim(java.time.Instant.now(), 30);
        job.fail("Provider failed");
        when(repository.lockByIdAndUserId(8L, 7L)).thenReturn(Optional.of(job));
        doThrow(ApiException.forbidden("AI_TIER_REQUIRED", "Denied")).when(subscriptions).requireAi(7L);
        assertThatThrownBy(() -> service.retry(7L, 8L)).isInstanceOf(ApiException.class);
        assertThat(job.getStatus()).isEqualTo(AiGeneration.Status.FAILED);
        doNothing().when(subscriptions).requireAi(7L);
        assertThat(service.retry(7L, 8L).status()).isEqualTo("PENDING");
        job.claim(java.time.Instant.now(), 30);
        assertThat(job.getMockChargeCount()).isEqualTo(1);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThatThrownBy(() -> service.retry(7L, 8L)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("AI_RETRY_NOT_ALLOWED");
    }
    private AiGenerationService.CreateGenerationRequest request(String url, String prompt) {
        return new AiGenerationService.CreateGenerationRequest(url, prompt, AiGeneration.Type.IMAGE_TO_IMAGE);
    }
}
