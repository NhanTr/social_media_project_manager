package com.nhantr.ai_service.service;

import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.provider.AiProvider;
import com.nhantr.ai_service.repository.AiGenerationRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AiGenerationWorker implements AutoCloseable {
    private final AiGenerationRepository repository;
    private final AiProvider provider;
    private final TransactionTemplate transactions;
    private final long timeoutMs;
    private final long leaseSeconds;
    // Bounded concurrency even if a future real provider ignores interruption.
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("ai-provider").factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public AiGenerationWorker(AiGenerationRepository repository, AiProvider provider,
                              PlatformTransactionManager manager,
                              @Value("${ai.worker.timeout-ms:5000}") long timeoutMs,
                              @Value("${ai.worker.lease-seconds:30}") long leaseSeconds) {
        if (timeoutMs < 1 || leaseSeconds * 1000 <= timeoutMs) {
            throw new IllegalArgumentException("AI lease must exceed the positive provider timeout");
        }
        this.repository = repository;
        this.provider = provider;
        this.transactions = new TransactionTemplate(manager);
        this.timeoutMs = timeoutMs;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${ai.worker.poll-ms:500}", initialDelayString = "${ai.worker.poll-ms:500}")
    public void poll() {
        // Read committed rows only: a rolled-back create cannot escape to the provider.
        AiGeneration job = transactions.execute(status -> repository.claimable().map(generation -> {
            generation.claim(Instant.now(), leaseSeconds);
            return repository.save(generation);
        }).orElse(null));
        if (job == null) return;
        UUID token = job.getAttemptToken();
        String result = null;
        String error = null;
        Future<String> call = null;
        try {
            call = executor.submit(() -> provider.generate(job));
            result = call.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (result == null || result.isBlank()) error = "AI_PROVIDER_FAILED";
        } catch (TimeoutException timedOut) {
            error = "AI_PROVIDER_TIMEOUT";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            error = "AI_PROVIDER_INTERRUPTED";
        } catch (ExecutionException | RejectedExecutionException failed) {
            error = "AI_PROVIDER_FAILED";
        } finally {
            if (call != null && !call.isDone()) call.cancel(true);
        }
        complete(job.getId(), token, result, error);
    }

    void complete(Long id, UUID token, String result, String error) {
        transactions.executeWithoutResult(status -> repository.lockById(id).ifPresent(job -> {
            if (!job.accepts(token)) return;
            if (error == null) job.success(result); else job.fail(error);
            repository.save(job);
        }));
    }

    @Override @PreDestroy public void close() { executor.shutdownNow(); }
}
