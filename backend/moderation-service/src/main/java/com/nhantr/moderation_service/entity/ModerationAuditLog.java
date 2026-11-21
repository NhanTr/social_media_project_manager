package com.nhantr.moderation_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "moderation_audit_logs")
public class ModerationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "action_taken", nullable = false, columnDefinition = "report_action")
    private Report.Action actionTaken;

    @Column(name = "reason_note")
    private String reasonNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ModerationAuditLog() {}

    public ModerationAuditLog(
            Long reportId,
            Long adminId,
            String targetType,
            Long targetId,
            Report.Action actionTaken,
            String reasonNote
    ) {
        this.reportId = reportId;
        this.adminId = adminId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.actionTaken = actionTaken;
        this.reasonNote = reasonNote;
    }

    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public Long getAdminId() { return adminId; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Report.Action getActionTaken() { return actionTaken; }
    public String getReasonNote() { return reasonNote; }
    public Instant getCreatedAt() { return createdAt; }
}
