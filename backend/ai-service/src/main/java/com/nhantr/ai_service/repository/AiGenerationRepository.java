package com.nhantr.ai_service.repository;

import com.nhantr.ai_service.entity.AiGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, Long> {
    Optional<AiGeneration> findByIdAndUserId(Long id, Long userId);
}
