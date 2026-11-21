package com.nhantr.moderation_service.service;

import com.nhantr.moderation_service.client.IdentityClient;
import com.nhantr.moderation_service.client.NotificationClient;
import com.nhantr.moderation_service.client.PostClient;
import com.nhantr.moderation_service.entity.ModerationAuditLog;
import com.nhantr.moderation_service.entity.Report;
import com.nhantr.moderation_service.testutil.TestModerationRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationWorkflowTests {
    private TestModerationRepositories.FakeReportRepository reportRepository;
    private TestModerationRepositories.FakeModerationAuditLogRepository auditLogRepository;
    private IdentityClient identityClient;
    private PostClient postClient;
    private NotificationClient notificationClient;
    private ModerationService service;

    private static final Long ADMIN_ID = 99L;
    private static final Long POST_ID = 200L;
    private static final Long REPORTED_USER_ID = 50L;

    @BeforeEach
    void setUp() {
        reportRepository = new TestModerationRepositories.FakeReportRepository();
        auditLogRepository = new TestModerationRepositories.FakeModerationAuditLogRepository();
        identityClient = mock(IdentityClient.class);
        postClient = mock(PostClient.class);
        notificationClient = mock(NotificationClient.class);

        service = new ModerationService(
                reportRepository,
                auditLogRepository,
                identityClient,
                postClient,
                notificationClient
        );

        when(identityClient.exists(eq(REPORTED_USER_ID))).thenReturn(true);
        when(postClient.get(eq(POST_ID)))
                .thenReturn(new PostClient.PostSummary(POST_ID, REPORTED_USER_ID, "ACTIVE"));
    }

    @Test
    void adminResolvesHidePostSuccess() {
        Long reporterId = 1L;
        var created = service.create(reporterId, new ModerationService.CreateReportRequest(POST_ID, null, "Graphic violence"));

        var resolveReq = new ModerationService.ResolveReportRequest(Report.Action.HIDE_POST, "Violates widget safety guidelines");
        var resolved = service.resolve(ADMIN_ID, created.id(), resolveReq);

        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.actionTaken()).isEqualTo("HIDE_POST");
        assertThat(resolved.handledByAdminId()).isEqualTo(ADMIN_ID);
        assertThat(resolved.resolutionNote()).isEqualTo("Violates widget safety guidelines");

        // Verify downstream call to hide post
        verify(postClient).status(eq(POST_ID), eq("HIDDEN"));

        // Verify reporter notification
        verify(notificationClient).reportResult(eq(reporterId), eq(created.id()), contains("HIDE_POST"));

        // Verify immutable audit log
        List<ModerationAuditLog> audits = auditLogRepository.findByReportIdOrderByCreatedAtDesc(created.id());
        assertThat(audits).hasSize(1);
        ModerationAuditLog log = audits.getFirst();
        assertThat(log.getAdminId()).isEqualTo(ADMIN_ID);
        assertThat(log.getTargetType()).isEqualTo("POST");
        assertThat(log.getTargetId()).isEqualTo(POST_ID);
        assertThat(log.getActionTaken()).isEqualTo(Report.Action.HIDE_POST);
        assertThat(log.getReasonNote()).isEqualTo("Violates widget safety guidelines");
    }

    @Test
    void adminResolvesBanUserSuccess() {
        Long reporterId = 2L;
        var created = service.create(reporterId, new ModerationService.CreateReportRequest(null, REPORTED_USER_ID, "Persistent spam bot"));

        var resolveReq = new ModerationService.ResolveReportRequest(Report.Action.BAN_USER, "Confirmed spam bot");
        var resolved = service.resolve(ADMIN_ID, created.id(), resolveReq);

        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.actionTaken()).isEqualTo("BAN_USER");

        verify(identityClient).ban(eq(REPORTED_USER_ID));
        verify(notificationClient).reportResult(eq(reporterId), eq(created.id()), contains("BAN_USER"));

        List<ModerationAuditLog> audits = auditLogRepository.findByReportIdOrderByCreatedAtDesc(created.id());
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getTargetType()).isEqualTo("USER");
        assertThat(audits.getFirst().getTargetId()).isEqualTo(REPORTED_USER_ID);
    }

    @Test
    void adminResolvesWarnUserSendsRealWarningNotification() {
        Long reporterId = 3L;
        var created = service.create(reporterId, new ModerationService.CreateReportRequest(null, REPORTED_USER_ID, "Mild offensive language"));

        var resolveReq = new ModerationService.ResolveReportRequest(Report.Action.WARN_USER, "First warning: keep language friendly");
        var resolved = service.resolve(ADMIN_ID, created.id(), resolveReq);

        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.actionTaken()).isEqualTo("WARN_USER");

        // Verify the reported user actually receives a warning notification!
        verify(notificationClient).notifyUser(eq(REPORTED_USER_ID), eq(created.id()), contains("First warning"));
    }

    @Test
    void adminRejectsReportSuccess() {
        Long reporterId = 4L;
        var created = service.create(reporterId, new ModerationService.CreateReportRequest(POST_ID, null, "I do not like this picture"));

        var rejectReq = new ModerationService.RejectReportRequest("Content does not violate community standards");
        var rejected = service.reject(ADMIN_ID, created.id(), rejectReq);

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.actionTaken()).isEqualTo("REJECT_REPORT");
        assertThat(rejected.resolutionNote()).isEqualTo("Content does not violate community standards");

        verify(notificationClient).reportResult(eq(reporterId), eq(created.id()), contains("REJECT_REPORT"));
    }

    @Test
    void cascadeResolutionAutomaticallyResolvesAllPendingReportsForSamePost() {
        Long user1 = 11L;
        Long user2 = 12L;
        Long user3 = 13L;

        var rep1 = service.create(user1, new ModerationService.CreateReportRequest(POST_ID, null, "Violence report 1"));
        var rep2 = service.create(user2, new ModerationService.CreateReportRequest(POST_ID, null, "Violence report 2"));
        var rep3 = service.create(user3, new ModerationService.CreateReportRequest(POST_ID, null, "Violence report 3"));

        // Admin resolves report #1
        service.resolve(ADMIN_ID, rep1.id(), new ModerationService.ResolveReportRequest(Report.Action.HIDE_POST, "Hide dangerous content"));

        // Verify reports #2 and #3 are also cascade resolved!
        Report updatedRep2 = reportRepository.findById(rep2.id()).orElseThrow();
        Report updatedRep3 = reportRepository.findById(rep3.id()).orElseThrow();

        assertThat(updatedRep2.getStatus()).isEqualTo(Report.Status.RESOLVED);
        assertThat(updatedRep2.getActionTaken()).isEqualTo(Report.Action.HIDE_POST);
        assertThat(updatedRep3.getStatus()).isEqualTo(Report.Status.RESOLVED);
        assertThat(updatedRep3.getActionTaken()).isEqualTo(Report.Action.HIDE_POST);

        // Verify all 3 reporters received result notifications
        verify(notificationClient, times(1)).reportResult(eq(user1), eq(rep1.id()), contains("HIDE_POST"));
        verify(notificationClient, times(1)).reportResult(eq(user2), eq(rep2.id()), contains("HIDE_POST"));
        verify(notificationClient, times(1)).reportResult(eq(user3), eq(rep3.id()), contains("HIDE_POST"));
    }

    @Test
    void listWithFiltersRespectsStatusAndPagination() {
        service.create(1L, new ModerationService.CreateReportRequest(POST_ID, null, "Report A"));
        var repB = service.create(2L, new ModerationService.CreateReportRequest(null, REPORTED_USER_ID, "Report B"));
        service.resolve(ADMIN_ID, repB.id(), new ModerationService.ResolveReportRequest(Report.Action.WARN_USER, "Warn"));

        // Filter by PENDING
        var pendingPage = service.listWithFilters(Report.Status.PENDING, 0, 10);
        assertThat(pendingPage.content()).hasSize(1);
        assertThat(pendingPage.content().getFirst().status()).isEqualTo("PENDING");

        // Filter by RESOLVED
        var resolvedPage = service.listWithFilters(Report.Status.RESOLVED, 0, 10);
        assertThat(resolvedPage.content()).hasSize(1);
        assertThat(resolvedPage.content().getFirst().status()).isEqualTo("RESOLVED");
    }
}
