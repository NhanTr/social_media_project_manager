package com.nhantr.moderation_service.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTests {
    @Test
    void finalDecisionCannotBeOverwritten() {
        Report report = new Report(1L, 2L, null, "Spam");
        report.resolve(10L, Report.Action.HIDE_POST, "Confirmed");
        assertThatThrownBy(() -> report.resolve(11L, Report.Action.DELETE_POST, "Changed"))
                .isInstanceOf(RuntimeException.class);
        assertThat(report.getHandledByAdminId()).isEqualTo(10L);
    }

    @Test
    void decisionRequiresAnAuditNote() {
        Report report = new Report(1L, 2L, null, "Spam");
        assertThatThrownBy(() -> report.resolve(10L, Report.Action.HIDE_POST, " "))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void resolveStoresFinalAction() {
        Report report = new Report(1L, 2L, null, "Spam");
        report.resolve(10L, Report.Action.HIDE_POST, "Confirmed");
        assertThat(report.getStatus()).isEqualTo(Report.Status.RESOLVED);
        assertThat(report.getActionTaken()).isEqualTo(Report.Action.HIDE_POST);
        assertThat(report.getHandledByAdminId()).isEqualTo(10L);
    }
}
