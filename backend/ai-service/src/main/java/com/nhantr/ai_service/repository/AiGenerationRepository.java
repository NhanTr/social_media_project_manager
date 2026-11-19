package com.nhantr.ai_service.repository;

import com.nhantr.ai_service.entity.AiGeneration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, Long> {
    Optional<AiGeneration> findByIdAndUserId(Long id, Long userId);

    Optional<AiGeneration> findByUserIdAndClientRequestId(Long userId, String clientRequestId);

    @Query("SELECT g FROM AiGeneration g WHERE (g.status = 'PENDING' AND (g.leasedUntil IS NULL OR g.leasedUntil < :now)) "
            + "OR (g.status = 'PROCESSING' AND g.leasedUntil < :now) ORDER BY g.createdAt ASC")
    List<AiGeneration> findClaimableJobs(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("UPDATE AiGeneration g SET g.status = 'PENDING', g.errorMessage = NULL, g.completedAt = NULL, "
            + "g.leaseOwner = NULL, g.leasedUntil = NULL, g.retryCount = g.retryCount + 1, g.updatedAt = :now "
            + "WHERE g.id = :id AND g.userId = :userId AND g.status = 'FAILED'")
    int atomicRetry(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now);
}
