package com.nhantr.moderation_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.moderation_service.client.IdentityClient;
import com.nhantr.moderation_service.client.NotificationClient;
import com.nhantr.moderation_service.client.PostClient;
import com.nhantr.moderation_service.entity.ModerationAuditLog;
import com.nhantr.moderation_service.entity.Report;
import com.nhantr.moderation_service.repository.ModerationAuditLogRepository;
import com.nhantr.moderation_service.repository.ReportRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ModerationService {
    private final ReportRepository reports;
    private final ModerationAuditLogRepository auditLogs;
    private final IdentityClient identity;
    private final PostClient posts;
    private final NotificationClient notifications;

    public ModerationService(
            ReportRepository reports,
            ModerationAuditLogRepository auditLogs,
            IdentityClient identity,
            PostClient posts,
            NotificationClient notifications) {
        this.reports = reports;
        this.auditLogs = auditLogs;
        this.identity = identity;
        this.posts = posts;
        this.notifications = notifications;
    }

    @Transactional
    public ReportResponse create(Long reporterId, CreateReportRequest request) {
        boolean hasPost = request.reportedPostId() != null;
        boolean hasUser = request.reportedUserId() != null;
        if (hasPost == hasUser) {
            throw ApiException.badRequest("REPORT_TARGET_INVALID",
                    "Exactly one reportedPostId or reportedUserId is required.");
        }

        if (hasPost) {
            PostClient.PostSummary post = posts.get(request.reportedPostId());
            if (post == null) {
                throw ApiException.notFound("POST_NOT_FOUND", "Reported post not found.");
            }
            if (reporterId.equals(post.userId())) {
                throw ApiException.badRequest("SELF_POST_REPORT_INVALID",
                        "Users cannot report their own posts. Use delete instead.");
            }
            if ("DELETED".equalsIgnoreCase(post.status()) || "HIDDEN".equalsIgnoreCase(post.status())) {
                throw ApiException.badRequest("POST_ALREADY_INACTIVE",
                        "Cannot report a post that is already deleted or hidden.");
            }
            if (reports.findByReporterIdAndReportedPostIdAndStatus(reporterId, request.reportedPostId(),
                    Report.Status.PENDING).isPresent()) {
                throw ApiException.conflict("REPORT_DUPLICATE_PENDING",
                        "You already have a pending report for this post.");
            }
        } else {
            if (reporterId.equals(request.reportedUserId())) {
                throw ApiException.badRequest("SELF_REPORT_INVALID", "Users cannot report themselves.");
            }
            if (!identity.exists(request.reportedUserId())) {
                throw ApiException.notFound("USER_NOT_FOUND", "Reported user not found.");
            }
            if (reports.findByReporterIdAndReportedUserIdAndStatus(reporterId, request.reportedUserId(),
                    Report.Status.PENDING).isPresent()) {
                throw ApiException.conflict("REPORT_DUPLICATE_PENDING",
                        "You already have a pending report for this user.");
            }
        }

        Report report = reports.save(new Report(
                reporterId, request.reportedPostId(), request.reportedUserId(), request.reason().trim()));
        long count = getTargetReportCount(report);
        return ReportResponse.from(report, count);
    }

    public List<ReportResponse> list() {
        return reports.findAllByOrderByCreatedAtDesc().stream()
                .map(r -> ReportResponse.from(r, getTargetReportCount(r)))
                .toList();
    }

    public PageReportResponse listWithFilters(Report.Status status, int page, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        int boundedPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(boundedPage, boundedLimit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Report> resultPage = (status != null)
                ? reports.findByStatus(status, pageable)
                : reports.findAll(pageable);

        List<ReportResponse> items = resultPage.getContent().stream()
                .map(r -> ReportResponse.from(r, getTargetReportCount(r)))
                .toList();

        return new PageReportResponse(
                items,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.hasNext());
    }

    public ReportResponse get(Long id) {
        Report report = find(id);
        return ReportResponse.from(report, getTargetReportCount(report));
    }

    public List<AuditLogResponse> getAuditLogs(Long reportId) {
        return auditLogs.findByReportIdOrderByCreatedAtDesc(reportId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    @Transactional
    public ReportResponse resolve(Long adminId, Long reportId, ResolveReportRequest request) {
        Report report = find(reportId);
        if (report.getStatus() != Report.Status.PENDING && report.getStatus() != Report.Status.INVESTIGATING) {
            throw ApiException.conflict("REPORT_ALREADY_CLOSED", "Report has already been closed.");
        }
        if (request.action() == null || request.action() == Report.Action.NONE) {
            throw ApiException.badRequest("REPORT_ACTION_INVALID", "A valid moderation action is required.");
        }

        // Thực thi tác vụ downstream trước với cơ chế fail-safe
        applyAction(report, request.action(), request.resolutionNote());

        Report.Status finalStatus = (request.action() == Report.Action.REJECT_REPORT)
                ? Report.Status.REJECTED
                : Report.Status.RESOLVED;

        // Cập nhật atomic tại DB level
        int updated = reports.atomicResolve(
                reportId,
                finalStatus,
                request.action(),
                adminId,
                normalize(request.resolutionNote()),
                Instant.now());

        if (updated == 0) {
            throw ApiException.conflict("REPORT_ALREADY_HANDLED",
                    "Report was already handled by another administrator.");
        }

        // Ghi Audit Log bất biến
        String targetType = report.getReportedPostId() != null ? "POST" : "USER";
        Long targetId = report.getReportedPostId() != null ? report.getReportedPostId() : report.getReportedUserId();
        auditLogs.save(new ModerationAuditLog(
                report.getId(),
                adminId,
                targetType,
                targetId,
                request.action(),
                normalize(request.resolutionNote())));

        // Thông báo kết quả cho người báo cáo
        notifications.reportResult(
                report.getReporterId(),
                report.getId(),
                "Báo cáo của bạn đã được xử lý với hành động: " + request.action().name());

        // Cascade resolution: Tự động gộp giải quyết các report PENDING khác cùng
        // target
        cascadeResolveSiblings(adminId, report, request.action(), request.resolutionNote());

        Report reloaded = find(reportId);
        return ReportResponse.from(reloaded, getTargetReportCount(reloaded));
    }

    @Transactional
    public ReportResponse reject(Long adminId, Long reportId, RejectReportRequest request) {
        return resolve(adminId, reportId,
                new ResolveReportRequest(Report.Action.REJECT_REPORT, request.resolutionNote()));
    }

    private void cascadeResolveSiblings(Long adminId, Report primary, Report.Action action, String note) {
        if (action == Report.Action.REJECT_REPORT) {
            return; // Không cascade khi reject report cá nhân
        }

        List<Report> siblings;
        if (primary.getReportedPostId() != null) {
            siblings = reports.findByReportedPostIdAndStatus(primary.getReportedPostId(), Report.Status.PENDING);
        } else {
            siblings = reports.findByReportedUserIdAndStatus(primary.getReportedUserId(), Report.Status.PENDING);
        }

        for (Report sibling : siblings) {
            if (!sibling.getId().equals(primary.getId())) {
                sibling.resolve(adminId, action, "Tự động gộp xử lý theo báo cáo #" + primary.getId() + ": " + note);
                reports.save(sibling);
                notifications.reportResult(
                        sibling.getReporterId(),
                        sibling.getId(),
                        "Báo cáo của bạn đã được xử lý theo quyết định kiểm duyệt chung: " + action.name());
            }
        }
    }

    private void applyAction(Report report, Report.Action action, String note) {
        switch (action) {
            case HIDE_POST -> {
                requirePost(report);
                posts.status(report.getReportedPostId(), "HIDDEN");
            }
            case DELETE_POST -> {
                requirePost(report);
                posts.status(report.getReportedPostId(), "DELETED");
            }
            case BAN_USER -> {
                requireUser(report);
                identity.ban(report.getReportedUserId());
            }
            case WARN_USER -> {
                requireUser(report);
                notifications.notifyUser(
                        report.getReportedUserId(),
                        report.getId(),
                        "Tài khoản của bạn đã nhận một cảnh cáo từ Quản trị viên: "
                                + (note != null ? note : "Vi phạm quy tắc cộng đồng"));
            }
            case REJECT_REPORT -> {
            }
            case NONE ->
                throw ApiException.badRequest("REPORT_ACTION_INVALID", "A final moderation action is required.");
        }
    }

    private void requirePost(Report report) {
        if (report.getReportedPostId() == null) {
            throw ApiException.badRequest("REPORT_ACTION_TARGET_MISMATCH", "This action requires a reported post.");
        }
    }

    private void requireUser(Report report) {
        if (report.getReportedUserId() == null) {
            throw ApiException.badRequest("REPORT_ACTION_TARGET_MISMATCH", "This action requires a reported user.");
        }
    }

    private long getTargetReportCount(Report report) {
        if (report.getReportedPostId() != null) {
            return reports.countByReportedPostIdAndStatus(report.getReportedPostId(), Report.Status.PENDING);
        } else if (report.getReportedUserId() != null) {
            return reports.countByReportedUserIdAndStatus(report.getReportedUserId(), Report.Status.PENDING);
        }
        return 1;
    }

    private Report find(Long id) {
        return reports.findById(id).orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "Report not found."));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateReportRequest(
            Long reportedPostId,
            Long reportedUserId,
            @NotNull @Size(min = 3, max = 2000) String reason) {
    }

    public record ResolveReportRequest(
            @NotNull Report.Action action,
            @Size(max = 2000) String resolutionNote) {
    }

    public record RejectReportRequest(
            @Size(max = 2000) String resolutionNote) {
    }

    public record ReportResponse(
            Long id,
            Long reporterId,
            Long reportedPostId,
            Long reportedUserId,
            String reason,
            String status,
            String actionTaken,
            Long handledByAdminId,
            String resolutionNote,
            Instant createdAt,
            Instant resolvedAt,
            long pendingReportCount) {
        public static ReportResponse from(Report report, long pendingReportCount) {
            return new ReportResponse(
                    report.getId(),
                    report.getReporterId(),
                    report.getReportedPostId(),
                    report.getReportedUserId(),
                    report.getReason(),
                    report.getStatus().name(),
                    report.getActionTaken().name(),
                    report.getHandledByAdminId(),
                    report.getResolutionNote(),
                    report.getCreatedAt(),
                    report.getResolvedAt(),
                    pendingReportCount);
        }
    }

    public record PageReportResponse(
            List<ReportResponse> content,
            int page,
            int limit,
            long totalElements,
            int totalPages,
            boolean hasNext) {
    }

    public record AuditLogResponse(
            Long id,
            Long reportId,
            Long adminId,
            String targetType,
            Long targetId,
            String actionTaken,
            String reasonNote,
            Instant createdAt) {
        public static AuditLogResponse from(ModerationAuditLog log) {
            return new AuditLogResponse(
                    log.getId(),
                    log.getReportId(),
                    log.getAdminId(),
                    log.getTargetType(),
                    log.getTargetId(),
                    log.getActionTaken().name(),
                    log.getReasonNote(),
                    log.getCreatedAt());
        }
    }
}
