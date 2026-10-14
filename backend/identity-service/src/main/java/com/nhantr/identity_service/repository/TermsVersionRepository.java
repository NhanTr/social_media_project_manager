package com.nhantr.identity_service.repository;

import com.nhantr.identity_service.entity.TermsVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TermsVersionRepository extends JpaRepository<TermsVersion, Long> {
    Optional<TermsVersion> findFirstByActiveTrueOrderByPublishedAtDesc();
}
