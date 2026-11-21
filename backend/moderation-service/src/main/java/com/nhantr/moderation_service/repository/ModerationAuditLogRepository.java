package com.nhantr.moderation_service.repository;

import com.nhantr.moderation_service.entity.ModerationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationAuditLogRepository extends JpaRepository<ModerationAuditLog, Long> {
    List<ModerationAuditLog> findByReportIdOrderByCreatedAtDesc(Long reportId);

    List<ModerationAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    List<ModerationAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    List<ModerationAuditLog> findAllByOrderByCreatedAtDesc();
}
