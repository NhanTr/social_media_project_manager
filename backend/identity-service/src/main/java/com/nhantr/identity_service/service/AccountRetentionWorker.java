package com.nhantr.identity_service.service;

import com.nhantr.identity_service.entity.AccountDeletionRequest;
import com.nhantr.identity_service.repository.AccountDeletionRequestRepository;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class AccountRetentionWorker {
    private final AccountDeletionRequestRepository requests;
    private final AccountRetentionService service;
    @Value("${retention.accounts.enabled:false}") private boolean enabled;
    @Value("${retention.accounts.dry-run:true}") private boolean dryRun;
    public AccountRetentionWorker(AccountDeletionRequestRepository requests, AccountRetentionService service) {
        this.requests = requests; this.service = service;
    }
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void tick() { if (enabled) run(dryRun); }
    public Map<String, Integer> run(boolean preview) {
        Map<String, Integer> results = new LinkedHashMap<>();
        for (var request : requests.findTop25ByStatusAndScheduledPurgeAtBeforeOrderByScheduledPurgeAtAsc(AccountDeletionRequest.Status.PENDING, Instant.now())) {
            try { results.merge(service.process(request.getId(), preview), 1, Integer::sum); }
            catch (RuntimeException exception) { results.merge("FAILED_RETRYABLE", 1, Integer::sum); }
        }
        return results;
    }
}
