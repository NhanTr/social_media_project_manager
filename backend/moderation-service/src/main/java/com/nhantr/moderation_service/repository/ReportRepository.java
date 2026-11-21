package com.nhantr.moderation_service.repository;

import com.nhantr.moderation_service.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByCreatedAtDesc();

    Page<Report> findByStatus(Report.Status status, Pageable pageable);

    Optional<Report> findByReporterIdAndReportedPostIdAndStatus(
            Long reporterId,
            Long reportedPostId,
            Report.Status status
    );

    Optional<Report> findByReporterIdAndReportedUserIdAndStatus(
            Long reporterId,
            Long reportedUserId,
            Report.Status status
    );

    List<Report> findByReportedPostIdAndStatus(Long reportedPostId, Report.Status status);

    List<Report> findByReportedUserIdAndStatus(Long reportedUserId, Report.Status status);

    long countByReportedPostIdAndStatus(Long reportedPostId, Report.Status status);

    long countByReportedUserIdAndStatus(Long reportedUserId, Report.Status status);

    @Modifying
    @Query("UPDATE Report r SET r.status = :newStatus, r.actionTaken = :action, "
            + "r.handledByAdminId = :adminId, r.resolutionNote = :note, r.resolvedAt = :now "
            + "WHERE r.id = :id AND r.status = 'PENDING'")
    int atomicResolve(
            @Param("id") Long id,
            @Param("newStatus") Report.Status newStatus,
            @Param("action") Report.Action action,
            @Param("adminId") Long adminId,
            @Param("note") String note,
            @Param("now") Instant now
    );
}
