package com.nhantr.identity_service.repository;

import com.nhantr.identity_service.entity.AccountDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {
    boolean existsByUserIdAndStatus(Long userId, AccountDeletionRequest.Status status);
}
