package com.nhantr.moderation_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.moderation_service.entity.Report;
import com.nhantr.moderation_service.entity.ModerationAudit;
import com.nhantr.moderation_service.repository.ReportRepository;
import com.nhantr.moderation_service.repository.ModerationAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.function.Consumer;

/** Each phase commits independently; an uncertain remote outcome retains its original decision for retry. */
@Service
public class ModerationDecisionStore {
    private final ReportRepository reports;
    private final ModerationAuditRepository audits;
    public ModerationDecisionStore(ReportRepository reports, ModerationAuditRepository audits) {
        this.reports = reports; this.audits = audits;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Report prepare(Long id, Long adminId, Report.Action action, String note) {
        Report report = lock(id);
        boolean first = report.getActionTaken() == Report.Action.NONE;
        report.prepare(adminId, action, note);
        report.recordAttempt();
        if (first) audits.save(new ModerationAudit(report, "REQUESTED"));
        return report;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Report apply(Long id, Consumer<Report> effect) {
        Report report = lock(id);
        if (!report.closed()) {
            effect.accept(report);
            report.resolve(report.getHandledByAdminId(), report.getActionTaken(), report.getResolutionNote());
            audits.save(new ModerationAudit(report, "APPLIED"));
        }
        return report;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Long id) {
        Report report = lock(id);
        if (!report.closed()) audits.save(new ModerationAudit(report, "DOWNSTREAM_UNCONFIRMED"));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyResult(Long id, Consumer<Report> effect) {
        Report report = lock(id);
        if (report.closed() && !report.isNotificationDelivered()) {
            effect.accept(report);
            report.notificationDelivered();
            audits.save(new ModerationAudit(report, "RESULT_NOTIFIED"));
        }
    }
    private Report lock(Long id) {
        return reports.lock(id).orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "Report not found."));
    }
}
