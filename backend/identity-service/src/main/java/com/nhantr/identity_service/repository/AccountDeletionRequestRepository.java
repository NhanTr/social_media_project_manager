package com.nhantr.identity_service.repository;

import com.nhantr.identity_service.entity.AccountDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from AccountDeletionRequest r where r.id = :id")
    java.util.Optional<AccountDeletionRequest> lockById(Long id);
    java.util.List<AccountDeletionRequest> findTop25ByStatusAndScheduledPurgeAtBeforeOrderByScheduledPurgeAtAsc(AccountDeletionRequest.Status status, java.time.Instant boundary);
    boolean existsByUserIdAndStatus(Long userId, AccountDeletionRequest.Status status);
}
