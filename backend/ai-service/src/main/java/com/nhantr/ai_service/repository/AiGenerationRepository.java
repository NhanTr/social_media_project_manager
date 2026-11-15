package com.nhantr.ai_service.repository;

import com.nhantr.ai_service.entity.AiGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from AiGeneration g where g.id = :id and g.userId = :userId")
    Optional<AiGeneration> lockByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from AiGeneration g where g.id = :id")
    Optional<AiGeneration> lockById(Long id);

    @Query(value = "SELECT * FROM ai_generations WHERE status = 'PENDING' OR (status = 'PROCESSING' AND (lease_until IS NULL OR lease_until < now())) ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<AiGeneration> claimable();

    Optional<AiGeneration> findByIdAndUserId(Long id, Long userId);
}
