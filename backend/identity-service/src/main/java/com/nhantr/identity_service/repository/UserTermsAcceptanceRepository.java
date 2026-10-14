package com.nhantr.identity_service.repository;

import com.nhantr.identity_service.entity.UserTermsAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsAcceptanceRepository extends JpaRepository<UserTermsAcceptance, Long> {
    boolean existsByUserIdAndTermsVersionId(Long userId, Long termsVersionId);
}
