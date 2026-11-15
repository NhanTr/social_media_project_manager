package com.nhantr.moderation_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.moderation_service.client.IdentityClient;
import com.nhantr.moderation_service.client.PostClient;
import com.nhantr.moderation_service.client.NotificationClient;
import com.nhantr.moderation_service.entity.Report;
import com.nhantr.moderation_service.entity.ModerationAudit;
import com.nhantr.moderation_service.repository.ReportRepository;
import com.nhantr.moderation_service.repository.ModerationAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class ModerationServiceTests {
    final ReportRepository reports = mock(ReportRepository.class);
    final ModerationAuditRepository audits = mock(ModerationAuditRepository.class);
    final IdentityClient identity = mock(IdentityClient.class);
    final PostClient posts = mock(PostClient.class);
    final NotificationClient notifications = mock(NotificationClient.class);
    final ModerationDecisionStore store = new ModerationDecisionStore(reports, audits);
    final ModerationService service = new ModerationService(reports, identity, posts, notifications, store, audits);
    Report report(Long post, Long user) {
        Report report = new Report(1L, post, user, "Spam");
        ReflectionTestUtils.setField(report, "id", 3L);
        when(reports.findById(3L)).thenReturn(Optional.of(report));
        when(reports.lock(3L)).thenReturn(Optional.of(report));
        return report;
    }
    ModerationService.ResolveReportRequest decision(Report.Action action) {
        return new ModerationService.ResolveReportRequest(action, "Confirmed violation");
    }
    @Test void missingNoteCannotPerformIrreversibleAction() {
        report(2L, null);
        assertThatThrownBy(() -> service.resolve(9L, 3L,
                new ModerationService.ResolveReportRequest(Report.Action.HIDE_POST, " "))).isInstanceOf(ApiException.class);
        verifyNoInteractions(posts, audits);
    }
    @Test void failedHideRetainsIntentAndRetryDoesNotChangeActorOrNote() {
        var report = report(2L, null);
        doThrow(new RuntimeException("timeout")).doNothing().when(posts).status(2L, "HIDDEN");
        assertThatThrownBy(() -> service.resolve(9L, 3L, decision(Report.Action.HIDE_POST)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("MODERATION_DOWNSTREAM_UNCONFIRMED");
        assertThat(report.getStatus()).isEqualTo(Report.Status.INVESTIGATING);
        assertThat(report.getResolvedAt()).isNull();
        assertThatThrownBy(() -> service.resolve(10L, 3L, decision(Report.Action.HIDE_POST))).isInstanceOf(ApiException.class);
        service.resolve(9L, 3L, decision(Report.Action.HIDE_POST));
        service.resolve(9L, 3L, decision(Report.Action.HIDE_POST));
        assertThat(report.getStatus()).isEqualTo(Report.Status.RESOLVED);
        verify(posts, times(2)).status(2L, "HIDDEN");
        verify(notifications, times(1)).reportResult(eq(1L), eq(3L), anyString());
        var events = org.mockito.ArgumentCaptor.forClass(ModerationAudit.class);
        verify(audits, times(4)).save(events.capture());
        assertThat(events.getAllValues()).extracting(ModerationAudit::getOutcome)
                .containsExactly("REQUESTED", "DOWNSTREAM_UNCONFIRMED", "APPLIED", "RESULT_NOTIFIED");
    }
    @Test void failedNotificationDoesNotRollBackBanAndReplayRetriesOnlyNotification() {
        var report = report(null, 4L);
        doThrow(new RuntimeException("offline")).doNothing().when(notifications).reportResult(eq(1L), eq(3L), anyString());
        service.resolve(9L, 3L, decision(Report.Action.BAN_USER));
        assertThat(report.getStatus()).isEqualTo(Report.Status.RESOLVED);
        assertThat(report.isNotificationDelivered()).isFalse();
        service.resolve(9L, 3L, decision(Report.Action.BAN_USER));
        assertThat(report.isNotificationDelivered()).isTrue();
        verify(identity, times(1)).ban(4L);
        verify(notifications, times(2)).reportResult(eq(1L), eq(3L), anyString());
    }
    @Test void warningNotifiesTargetAndReporterOnce() {
        report(null, 4L);
        service.resolve(9L, 3L, decision(Report.Action.WARN_USER));
        service.resolve(9L, 3L, decision(Report.Action.WARN_USER));
        verify(notifications).warning(4L, 3L, "Confirmed violation");
        verify(notifications).reportResult(eq(1L), eq(3L), anyString());
    }
    @Test void rejectDoesNotCallPostOrIdentity() {
        var report = report(2L, null);
        service.reject(9L, 3L, new ModerationService.RejectReportRequest("No violation"));
        assertThat(report.getStatus()).isEqualTo(Report.Status.REJECTED);
        verifyNoInteractions(posts, identity);
    }
    @Test void duplicatePostReportIsRejectedBeforeCallingOwner() {
        when(reports.existsByReporterIdAndReportedPostId(1L, 2L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(1L, new ModerationService.CreateReportRequest(2L, null, "Spam")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("REPORT_DUPLICATE");
        verifyNoInteractions(posts);
        verify(reports, never()).saveAndFlush(any());
    }
    @Test void queueFiltersAndUsesStableCursor() {
        var one = report(2L, null);
        var two = new Report(2L, 4L, null, "Spam");
        ReflectionTestUtils.setField(two, "id", 2L);
        when(reports.queue(eq(Report.Status.PENDING), isNull(), any())).thenReturn(List.of(one, two));
        var page = service.queue(Report.Status.PENDING, null, 1);
        assertThat(page.items()).extracting(ModerationService.ReportResponse::id).containsExactly(3L);
        assertThat(page.hasNext()).isTrue();
        when(reports.queue(eq(Report.Status.PENDING), eq(3L), any())).thenReturn(List.of(two));
        assertThat(service.queue(Report.Status.PENDING, page.nextCursor(), 1).items()).hasSize(1);
        assertThatThrownBy(() -> service.queue(null, "???", 1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.queue(null, null, 101)).isInstanceOf(ApiException.class);
    }
    @Test void recoveryCompletesPendingIntentAndNotification() {
        var report = report(null, 4L);
        report.prepare(9L, Report.Action.BAN_USER, "Confirmed violation");
        when(reports.pendingActions(any(), any()))
                .thenReturn(List.of(report));
        service.recoverPending();
        assertThat(report.getStatus()).isEqualTo(Report.Status.RESOLVED);
        assertThat(report.isNotificationDelivered()).isTrue();
        verify(identity).ban(4L);
    }
    @Test void mismatchedActionDoesNotRecordAnIntent() {
        report(2L, null);
        assertThatThrownBy(() -> service.resolve(9L, 3L, decision(Report.Action.BAN_USER))).isInstanceOf(ApiException.class);
        verifyNoInteractions(audits, identity);
    }
}
