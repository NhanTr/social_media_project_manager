package com.nhantr.moderation_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.moderation_service.client.IdentityClient;
import com.nhantr.moderation_service.client.NotificationClient;
import com.nhantr.moderation_service.client.PostClient;
import com.nhantr.moderation_service.entity.Report;
import com.nhantr.moderation_service.repository.ReportRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import com.nhantr.common.api.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.nhantr.moderation_service.repository.ModerationAuditRepository;
import com.nhantr.moderation_service.entity.ModerationAudit;
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

    private final ModerationDecisionStore decisions;
    private final ModerationAuditRepository audits;

    public ModerationService(
            ReportRepository reports,
            IdentityClient identity,
            PostClient posts,
            NotificationClient notifications,
            ModerationDecisionStore decisions, ModerationAuditRepository audits
    ) {
        this.reports = reports;
        this.identity = identity;
        this.posts = posts;
        this.notifications = notifications;
        this.decisions = decisions; this.audits = audits;
    }

    @Transactional
    public ReportResponse create(Long reporterId, CreateReportRequest request) {
        boolean hasPost = request.reportedPostId() != null;
        boolean hasUser = request.reportedUserId() != null;
        if (hasPost == hasUser) {
            throw ApiException.badRequest("REPORT_TARGET_INVALID", "Exactly one reportedPostId or reportedUserId is required.");
        }
        if (hasPost ? reports.existsByReporterIdAndReportedPostId(reporterId, request.reportedPostId())
                : reports.existsByReporterIdAndReportedUserId(reporterId, request.reportedUserId())) {
            throw ApiException.conflict("REPORT_DUPLICATE", "You already reported this target.");
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
        try {
            return ReportResponse.from(reports.saveAndFlush(new Report(
                    reporterId, request.reportedPostId(), request.reportedUserId(), request.reason().trim()
            )));
        } catch (DataIntegrityViolationException duplicate) {
            throw ApiException.conflict("REPORT_DUPLICATE", "Report target was already submitted or is no longer available.");
        }
    }

    public List<ReportResponse> list() {
        return reports.findAllByOrderByCreatedAtDesc().stream().map(ReportResponse::from).toList();
    }

    public ReportResponse get(Long id) {
        return ReportResponse.from(find(id));
    }

    public PageResponse<ReportResponse> queue(Report.Status status, String cursor, int limit) {
        if (limit < 1 || limit > 100) throw ApiException.badRequest("PAGE_LIMIT_INVALID", "Limit must be between 1 and 100.");
        Long before = null;
        if (cursor != null) {
            try {
                before = Long.valueOf(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
                if (before < 1) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) { throw ApiException.badRequest("CURSOR_INVALID", "Invalid report cursor."); }
        }
        List<Report> rows = reports.queue(status, before, PageRequest.of(0, limit + 1));
        boolean more = rows.size() > limit;
        List<Report> selected = rows.subList(0, Math.min(limit, rows.size()));
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(
                selected.getLast().getId().toString().getBytes(StandardCharsets.UTF_8)) : null;
        return new PageResponse<>(selected.stream().map(ReportResponse::from).toList(), next, more);
    }

    public List<ModerationAudit> audit(Long id) { find(id); return audits.findByReportIdOrderByIdAsc(id); }

    public ReportResponse resolve(Long adminId, Long reportId, ResolveReportRequest request) {
        if (request.resolutionNote() == null || request.resolutionNote().isBlank())
            throw ApiException.badRequest("RESOLUTION_NOTE_REQUIRED", "An audit note is required.");
        Report current = find(reportId);
        validateTarget(current, request.action());
        decisions.prepare(reportId, adminId, request.action(), request.resolutionNote());
        Report result;
        try { result = decisions.apply(reportId, report -> applyAction(report, report.getActionTaken())); }
        catch (RuntimeException failure) {
            decisions.failed(reportId);
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "MODERATION_DOWNSTREAM_UNCONFIRMED",
                    "Action outcome is unconfirmed. Retry the same action, admin and note.");
        }
        try {
            decisions.notifyResult(reportId, report -> notifications.reportResult(report.getReporterId(), report.getId(),
                    "Report resolved with action " + report.getActionTaken().name()));
        } catch (RuntimeException ignored) {
            // Final action is durable. Repeating the same decision retries the durable notification marker.
        }
        return ReportResponse.from(result);
    }

    public ReportResponse reject(Long adminId, Long reportId, RejectReportRequest request) {
        return resolve(adminId, reportId, new ResolveReportRequest(Report.Action.REJECT_REPORT, request.resolutionNote()));
    }

    private void validateTarget(Report report, Report.Action action) {
        if (action == null || action == Report.Action.NONE) throw ApiException.badRequest("REPORT_ACTION_INVALID", "A final action is required.");
        switch (action) {
            case HIDE_POST, DELETE_POST -> requirePost(report);
            case BAN_USER, WARN_USER -> requireUser(report);
            default -> { }
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${app.moderation.recovery-delay-ms:10000}")
    public void recoverPending() {
        java.util.Map<Long, Report> pending = new java.util.LinkedHashMap<>();
        reports.pendingActions(Instant.now(), PageRequest.of(0, 50))
                .forEach(report -> pending.put(report.getId(), report));
        reports.pendingNotifications(Instant.now(), PageRequest.of(0, 50))
                .forEach(report -> pending.put(report.getId(), report));
        pending.values().forEach(report -> {
            if (report.getHandledByAdminId() == null || report.getResolutionNote() == null) return;
            try { resolve(report.getHandledByAdminId(), report.getId(),
                    new ResolveReportRequest(report.getActionTaken(), report.getResolutionNote())); }
            catch (RuntimeException ignored) {
                org.slf4j.LoggerFactory.getLogger(ModerationService.class).warn("Moderation recovery pending reportId={}", report.getId());
            }
        });
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
            case WARN_USER -> {
                requireUser(report);
                notifications.warning(report.getReportedUserId(), report.getId(), report.getResolutionNote());
            }
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

    public record CreateReportRequest(
            Long reportedPostId,
            Long reportedUserId,
            @NotNull @Size(min = 3, max = 2000) String reason
    ) {}
    public record ResolveReportRequest(@NotNull Report.Action action, @NotBlank @Size(max = 2000) String resolutionNote) {}
    public record RejectReportRequest(@NotBlank @Size(max = 2000) String resolutionNote) {}
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
