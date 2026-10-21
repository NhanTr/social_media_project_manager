package com.nhantr.moderation_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "reports")
public class Report {
    public enum Status { PENDING, INVESTIGATING, RESOLVED, REJECTED }
    public enum Action { NONE, HIDE_POST, DELETE_POST, WARN_USER, BAN_USER, REJECT_REPORT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;
    @Column(name = "reported_post_id")
    private Long reportedPostId;
    @Column(name = "reported_user_id")
    private Long reportedUserId;
    @Column(nullable = false)
    private String reason;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "report_status")
    private Status status = Status.PENDING;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "action_taken", nullable = false, columnDefinition = "report_action")
    private Action actionTaken = Action.NONE;
    @Column(name = "handled_by_admin_id")
    private Long handledByAdminId;
    @Column(name = "resolution_note")
    private String resolutionNote;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Report() {}
    public Report(Long reporterId, Long postId, Long userId, String reason) {
        this.reporterId = reporterId;
        this.reportedPostId = postId;
        this.reportedUserId = userId;
        this.reason = reason;
    }
    public Long getId() { return id; }
    public Long getReporterId() { return reporterId; }
    public Long getReportedPostId() { return reportedPostId; }
    public Long getReportedUserId() { return reportedUserId; }
    public String getReason() { return reason; }
    public Status getStatus() { return status; }
    public Action getActionTaken() { return actionTaken; }
    public Long getHandledByAdminId() { return handledByAdminId; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void resolve(Long adminId, Action action, String note) {
        status = action == Action.REJECT_REPORT ? Status.REJECTED : Status.RESOLVED;
        actionTaken = action;
        handledByAdminId = adminId;
        resolutionNote = note;
        resolvedAt = Instant.now();
    }
}
