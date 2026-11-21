package com.nhantr.moderation_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.moderation_service.client.IdentityClient;
import com.nhantr.moderation_service.client.NotificationClient;
import com.nhantr.moderation_service.client.PostClient;
import com.nhantr.moderation_service.entity.Report;
import com.nhantr.moderation_service.testutil.TestModerationRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationConcurrencyAndResilienceTests {
    private TestModerationRepositories.FakeReportRepository reportRepository;
    private TestModerationRepositories.FakeModerationAuditLogRepository auditLogRepository;
    private IdentityClient identityClient;
    private PostClient postClient;
    private NotificationClient notificationClient;
    private ModerationService service;

    private static final Long POST_ID = 300L;
    private static final Long REPORTED_USER_ID = 88L;

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
    void concurrentAdminResolutionAllowsOnlyOneToSucceed() throws InterruptedException {
        var created = service.create(1L, new ModerationService.CreateReportRequest(POST_ID, null, "Concurrent check"));

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final long adminId = 100L + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.resolve(adminId, created.id(), new ModerationService.ResolveReportRequest(Report.Action.HIDE_POST, "Note"));
                    successCount.incrementAndGet();
                } catch (ApiException e) {
                    if (e.status().value() == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(e);
                    }
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(unexpectedExceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(auditLogRepository.findAll()).hasSize(1);
    }

    @Test
    void downstreamFailureLeavesReportPendingAndDoesNotSaveAudit() {
        var created = service.create(1L, new ModerationService.CreateReportRequest(POST_ID, null, "Downstream fail test"));

        // Simulate postClient failure
        doThrow(new RuntimeException("Post service unavailable connection refused"))
                .when(postClient).status(eq(POST_ID), anyString());

        assertThatThrownBy(() -> service.resolve(99L, created.id(), new ModerationService.ResolveReportRequest(Report.Action.HIDE_POST, "Hide")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Post service unavailable");

        // Verify report remains PENDING in repository
        Report report = reportRepository.findById(created.id()).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(Report.Status.PENDING);
        assertThat(report.getActionTaken()).isEqualTo(Report.Action.NONE);

        // Verify no audit log was created
        assertThat(auditLogRepository.findByReportIdOrderByCreatedAtDesc(created.id())).isEmpty();
    }
}
