package com.nhantr.moderation_service.repository;
import com.nhantr.moderation_service.entity.ModerationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ModerationAuditRepository extends JpaRepository<ModerationAudit, Long> {
    List<ModerationAudit> findByReportIdOrderByIdAsc(Long reportId);
}
