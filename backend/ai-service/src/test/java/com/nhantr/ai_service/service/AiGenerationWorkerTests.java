package com.nhantr.ai_service.service;

import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.provider.MockAiProvider;
import com.nhantr.ai_service.repository.AiGenerationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiGenerationWorkerTests {
    @Test void workerCommitsSuccessForDeterministicProvider() {
        AiGeneration job = job("anime");
        try (var worker = worker(job, 1000)) {
            worker.poll();
            assertThat(job.getStatus()).isEqualTo(AiGeneration.Status.SUCCESS);
            assertThat(job.getGeneratedMediaUrl()).isEqualTo("https://media.test/a?aiGeneration=15");
            assertThat(job.getMockChargeCount()).isOne();
        }
    }
    @Test void providerFailureAndTimeoutPreserveOriginalAndPermitSameJobRetry() {
        for (String prompt : new String[]{"[fail]", "[timeout]"}) {
            AiGeneration job = job(prompt);
            try (var worker = worker(job, 20)) {
                worker.poll();
                assertThat(job.getStatus()).isEqualTo(AiGeneration.Status.FAILED);
                assertThat(job.getGeneratedMediaUrl()).isNull();
                assertThat(job.getOriginalMediaUrl()).isEqualTo("https://media.test/a");
                assertThat(job.getErrorMessage()).isNotBlank();
                job.retry();
                worker.poll();
                assertThat(job.getMockChargeCount()).isOne();
                assertThat(job.getAttemptCount()).isEqualTo(2);
            }
        }
    }
    @Test void staleAttemptTokenCannotCompleteRecoveredLease() {
        AiGeneration job = job("anime");
        job.claim(java.time.Instant.now().minusSeconds(60), 1);
        var old = job.getAttemptToken();
        job.claim(java.time.Instant.now(), 30);
        assertThat(job.accepts(old)).isFalse();
        assertThat(job.accepts(job.getAttemptToken())).isTrue();
        assertThat(job.getMockChargeCount()).isOne();
    }
    @Test void lateProviderCannotOverwriteRecoveredAttempt() {
        AiGeneration job = job("anime");
        job.claim(java.time.Instant.now().minusSeconds(60), 1);
        var old = job.getAttemptToken();
        job.claim(java.time.Instant.now(), 30);
        try (var worker = worker(job, 1000)) {
            worker.complete(15L, old, "https://media.test/stale", null);
            assertThat(job.getStatus()).isEqualTo(AiGeneration.Status.PROCESSING);
            assertThat(job.getGeneratedMediaUrl()).isNull();
            worker.complete(15L, job.getAttemptToken(), "https://media.test/current", null);
            assertThat(job.getGeneratedMediaUrl()).isEqualTo("https://media.test/current");
        }
    }
    private AiGeneration job(String prompt) {
        var job = new AiGeneration(7L, "https://media.test/a", prompt, AiGeneration.Type.IMAGE_TO_IMAGE);
        org.springframework.test.util.ReflectionTestUtils.setField(job, "id", 15L);
        return job;
    }
    private AiGenerationWorker worker(AiGeneration job, long timeoutMs) {
        var repository = mock(AiGenerationRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.claimable()).thenReturn(Optional.of(job));
        when(repository.lockById(15L)).thenReturn(Optional.of(job));
        return new AiGenerationWorker(repository, new MockAiProvider(), new Transactions(), timeoutMs, 30);
    }
    static class Transactions extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
