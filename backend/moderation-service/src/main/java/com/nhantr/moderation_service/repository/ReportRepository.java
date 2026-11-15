package com.nhantr.moderation_service.repository;

import com.nhantr.moderation_service.entity.Report;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByCreatedAtDesc();
    boolean existsByReporterIdAndReportedPostId(Long reporterId, Long postId);
    boolean existsByReporterIdAndReportedUserId(Long reporterId, Long userId);
    default List<Report> pendingActions(java.time.Instant now, Pageable page) {
        return pendingActionsQuery(Report.Status.INVESTIGATING, Report.Action.NONE, now, page);
    }
    @Query("select r from Report r where r.status = :status and r.actionTaken <> :excluded " +
            "and (r.nextAttemptAt is null or r.nextAttemptAt <= :now) order by r.nextAttemptAt asc nulls first, r.id asc")
    List<Report> pendingActionsQuery(Report.Status status, Report.Action excluded, java.time.Instant now, Pageable page);
    default List<Report> pendingNotifications(java.time.Instant now, Pageable page) {
        return pendingNotificationsQuery(List.of(Report.Status.RESOLVED, Report.Status.REJECTED), now, page);
    }
    @Query("select r from Report r where r.status in :statuses and r.notificationDelivered = false " +
            "and (r.nextAttemptAt is null or r.nextAttemptAt <= :now) order by r.nextAttemptAt asc nulls first, r.id asc")
    List<Report> pendingNotificationsQuery(List<Report.Status> statuses, java.time.Instant now, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    Optional<Report> lock(Long id);
    default List<Report> queue(Report.Status status, Long before, Pageable pageable) {
        long upperBound = before == null ? Long.MAX_VALUE : before;
        return status == null ? queueAll(upperBound, pageable) : queueByStatus(status, upperBound, pageable);
    }
    @Query("select r from Report r where r.id < :before order by r.id desc")
    List<Report> queueAll(Long before, Pageable pageable);
    @Query("select r from Report r where r.status = :status and r.id < :before order by r.id desc")
    List<Report> queueByStatus(Report.Status status, Long before, Pageable pageable);
}
