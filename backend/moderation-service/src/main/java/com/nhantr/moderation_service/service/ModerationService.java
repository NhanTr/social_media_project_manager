package com.nhantr.moderation_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.moderation_service.client.IdentityClient;
import com.nhantr.moderation_service.client.NotificationClient;
import com.nhantr.moderation_service.client.PostClient;
import com.nhantr.moderation_service.entity.Report;
import com.nhantr.moderation_service.repository.ReportRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ModerationService {
    private final ReportRepository reports;
    private final IdentityClient identity;
    private final PostClient posts;
    private final NotificationClient notifications;

    public ModerationService(
            ReportRepository reports,
            IdentityClient identity,
            PostClient posts,
            NotificationClient notifications
    ) {
        this.reports = reports;
        this.identity = identity;
        this.posts = posts;
        this.notifications = notifications;
    }

    @Transactional
    public ReportResponse create(Long reporterId, CreateReportRequest request) {
        boolean hasPost = request.reportedPostId() != null;
        boolean hasUser = request.reportedUserId() != null;
        if (hasPost == hasUser) {
            throw ApiException.badRequest("REPORT_TARGET_INVALID", "Exactly one reportedPostId or reportedUserId is required.");
        }
        if (hasPost) {
            posts.get(request.reportedPostId());
        } else {
            if (reporterId.equals(request.reportedUserId())) {
                throw ApiException.badRequest("SELF_REPORT_INVALID", "Users cannot report themselves.");
            }
            if (!identity.exists(request.reportedUserId())) {
                throw ApiException.notFound("USER_NOT_FOUND", "Reported user not found.");
            }
        }
        return ReportResponse.from(reports.save(new Report(
                reporterId, request.reportedPostId(), request.reportedUserId(), request.reason().trim()
        )));
    }

    public List<ReportResponse> list() {
        return reports.findAllByOrderByCreatedAtDesc().stream().map(ReportResponse::from).toList();
    }

    public ReportResponse get(Long id) {
        return ReportResponse.from(find(id));
    }

    @Transactional
    public ReportResponse resolve(Long adminId, Long reportId, ResolveReportRequest request) {
        Report report = find(reportId);
        if (report.getStatus() == Report.Status.RESOLVED || report.getStatus() == Report.Status.REJECTED) {
            throw ApiException.conflict("REPORT_ALREADY_CLOSED", "Report has already been closed.");
        }
        applyAction(report, request.action());
        report.resolve(adminId, request.action(), normalize(request.resolutionNote()));
        Report saved = reports.save(report);
        notifications.reportResult(report.getReporterId(), report.getId(), "Report resolved with action " + request.action().name());
        return ReportResponse.from(saved);
    }

    @Transactional
    public ReportResponse reject(Long adminId, Long reportId, RejectReportRequest request) {
        return resolve(adminId, reportId, new ResolveReportRequest(Report.Action.REJECT_REPORT, request.resolutionNote()));
    }

    private void applyAction(Report report, Report.Action action) {
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
            case WARN_USER -> requireUser(report);
            case REJECT_REPORT -> {}
            case NONE -> throw ApiException.badRequest("REPORT_ACTION_INVALID", "A final moderation action is required.");
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

    private Report find(Long id) {
        return reports.findById(id).orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "Report not found."));
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record CreateReportRequest(
            Long reportedPostId,
            Long reportedUserId,
            @NotNull @Size(min = 3, max = 2000) String reason
    ) {}
    public record ResolveReportRequest(@NotNull Report.Action action, @Size(max = 2000) String resolutionNote) {}
    public record RejectReportRequest(@Size(max = 2000) String resolutionNote) {}
    public record ReportResponse(
            Long id, Long reporterId, Long reportedPostId, Long reportedUserId, String reason,
            String status, String actionTaken, Long handledByAdminId, String resolutionNote,
            Instant createdAt, Instant resolvedAt
    ) {
        static ReportResponse from(Report report) {
            return new ReportResponse(
                    report.getId(), report.getReporterId(), report.getReportedPostId(), report.getReportedUserId(),
                    report.getReason(), report.getStatus().name(), report.getActionTaken().name(),
                    report.getHandledByAdminId(), report.getResolutionNote(), report.getCreatedAt(), report.getResolvedAt()
            );
        }
    }
}
