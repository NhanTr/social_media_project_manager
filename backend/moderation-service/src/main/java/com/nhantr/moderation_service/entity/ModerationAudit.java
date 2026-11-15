package com.nhantr.moderation_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "moderation_audit")
public class ModerationAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false) private Long reportId;
    @Column(nullable = false, updatable = false) private Long adminId;
    @Column(nullable = false, updatable = false) private String action;
    @Column(nullable = false, updatable = false) private String outcome;
    @Column(nullable = false, updatable = false) private String note;
    @Column(nullable = false, updatable = false) private Instant occurredAt;
    protected ModerationAudit() {}
    public ModerationAudit(Report report, String outcome) {
        reportId = report.getId(); adminId = report.getHandledByAdminId();
        action = report.getActionTaken().name(); note = report.getResolutionNote();
        this.outcome = outcome; occurredAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public Long getAdminId() { return adminId; }
    public String getAction() { return action; }
    public String getOutcome() { return outcome; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
}
