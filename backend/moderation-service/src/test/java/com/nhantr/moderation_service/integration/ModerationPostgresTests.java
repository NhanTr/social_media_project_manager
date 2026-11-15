package com.nhantr.moderation_service.integration;

import com.nhantr.common.api.ApiException;
import com.nhantr.moderation_service.client.IdentityClient;
import com.nhantr.moderation_service.client.PostClient;
import com.nhantr.moderation_service.client.NotificationClient;
import com.nhantr.moderation_service.entity.Report;
import com.nhantr.moderation_service.repository.ReportRepository;
import com.nhantr.moderation_service.repository.ModerationAuditRepository;
import com.nhantr.moderation_service.service.ModerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Path;
import java.nio.file.Files;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** All fixtures run in a newly created disposable PostgreSQL container. */
@SpringBootTest(properties = {"app.moderation.recovery-delay-ms=3600000"})
@Testcontainers(disabledWithoutDocker = true)
class ModerationPostgresTests {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("social_media_ai_db").withUsername("loccoc").withPassword("loccoc");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "moderation_service_user");
        registry.add("spring.datasource.password", () -> "moderation_password");
    }
    @BeforeAll static void migrate() throws Exception {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.isDirectory(directory.resolve("database/migration"))) directory = directory.getParent();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(directory.resolve("database/migration/V1__init_schema.sql")));
            // ScriptUtils splits PL/pgSQL bodies; execute V11 as one PostgreSQL statement batch instead.
            connection.createStatement().execute(Files.readString(directory.resolve("database/migration/V11__moderation_audit.sql")));
        }
    }
    @Autowired ModerationService service;
    @Autowired ReportRepository reports;
    @Autowired ModerationAuditRepository audits;
    @MockitoBean PostClient posts;
    @MockitoBean IdentityClient identity;
    @MockitoBean NotificationClient notifications;
    Long reporterId, targetId, adminId;
    Connection owner() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
    @BeforeEach void seed() throws Exception {
        try (var c = owner()) {
            reporterId = user(c); targetId = user(c);
            try (var s = c.prepareStatement("INSERT INTO admins(username,password_hash) VALUES (?, 'unused') RETURNING id")) {
                s.setString(1, "mod_" + UUID.randomUUID().toString().replace("-", ""));
                try (var rows = s.executeQuery()) { rows.next(); adminId = rows.getLong(1); }
            }
        }
        when(identity.exists(targetId)).thenReturn(true);
    }
    Long user(Connection c) throws SQLException {
        String name = "mod_" + UUID.randomUUID().toString().replace("-", "");
        try (var s = c.prepareStatement("INSERT INTO users(username,auth_provider,phone_number) VALUES (?,'PHONE',?) RETURNING id")) {
            s.setString(1, name); s.setString(2, UUID.randomUUID().toString().substring(0, 25));
            try (var rows = s.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }
    @Test void concurrentDuplicateSubmissionHasExactlyOneWinner() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> submit = () -> {
            start.await();
            try { service.create(reporterId, new ModerationService.CreateReportRequest(null, targetId, "Duplicate spam")); return true; }
            catch (ApiException conflict) { assertThat(conflict.code()).isEqualTo("REPORT_DUPLICATE"); return false; }
        };
        try {
            var a = pool.submit(submit); var b = pool.submit(submit); start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(reports.findAll().stream().filter(r -> r.getReporterId().equals(reporterId))).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void downstreamFailureCommitsIntentAndAuditThenRetryFinalizesOnce() {
        Long id = service.create(reporterId, new ModerationService.CreateReportRequest(null, targetId, "Spam account")).id();
        doThrow(new RuntimeException("timeout")).doNothing().when(identity).ban(targetId);
        var command = new ModerationService.ResolveReportRequest(Report.Action.BAN_USER, "Confirmed abuse");
        assertThatThrownBy(() -> service.resolve(adminId, id, command)).isInstanceOf(ApiException.class);
        var pending = reports.findById(id).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(Report.Status.INVESTIGATING);
        assertThat(pending.getHandledByAdminId()).isEqualTo(adminId);
        assertThat(pending.getResolvedAt()).isNull();
        assertThat(audits.findByReportIdOrderByIdAsc(id)).extracting(a -> a.getOutcome())
                .containsExactly("REQUESTED", "DOWNSTREAM_UNCONFIRMED");
        service.resolve(adminId, id, command); service.resolve(adminId, id, command);
        assertThat(reports.findById(id).orElseThrow().getStatus()).isEqualTo(Report.Status.RESOLVED);
        verify(identity, times(2)).ban(targetId);
        assertThat(audits.findByReportIdOrderByIdAsc(id)).extracting(a -> a.getOutcome())
                .containsExactly("REQUESTED", "DOWNSTREAM_UNCONFIRMED", "APPLIED", "RESULT_NOTIFIED");
    }
    @Test void queueFilterAndCursorExecuteAgainstPostgres() {
        service.create(reporterId, new ModerationService.CreateReportRequest(null, targetId, "Queue fixture"));
        assertThat(service.queue(null, null, 1).items()).hasSize(1);
        assertThat(service.queue(Report.Status.PENDING, null, 1).items()).hasSize(1);
        var page = service.queue(null, null, 1);
        if (page.hasNext()) assertThatCode(() -> service.queue(null, page.nextCursor(), 1)).doesNotThrowAnyException();
    }
    @Test void auditCannotBeUpdatedEvenByDatabaseOwner() throws Exception {
        Long id = service.create(reporterId, new ModerationService.CreateReportRequest(null, targetId, "Audit fixture")).id();
        service.reject(adminId, id, new ModerationService.RejectReportRequest("No violation found"));
        try (var c = owner(); var s = c.prepareStatement("UPDATE moderation_audit SET note='modified' WHERE report_id=?")) {
            s.setLong(1, id);
            assertThatThrownBy(s::executeUpdate).isInstanceOf(SQLException.class).hasMessageContaining("immutable");
        }
        assertThat(audits.findByReportIdOrderByIdAsc(id)).allSatisfy(a -> assertThat(a.getNote()).isEqualTo("No violation found"));
    }
    @Test void concurrentResolutionSerializesRemoteEffect() throws Exception {
        Long id = service.create(reporterId, new ModerationService.CreateReportRequest(null, targetId, "Concurrent action")).id();
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        var command = new ModerationService.ResolveReportRequest(Report.Action.BAN_USER, "Confirmed abuse");
        Callable<String> resolve = () -> { start.await(); return service.resolve(adminId, id, command).status(); };
        try {
            var a = pool.submit(resolve); var b = pool.submit(resolve); start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo("RESOLVED");
            assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo("RESOLVED");
            verify(identity, times(1)).ban(targetId);
            verify(notifications, times(1)).reportResult(eq(reporterId), eq(id), anyString());
            assertThat(audits.findByReportIdOrderByIdAsc(id)).extracting(audit -> audit.getOutcome())
                    .containsExactly("REQUESTED", "APPLIED", "RESULT_NOTIFIED");
        } finally { pool.shutdownNow(); }
    }

    @Test void failingOldestBatchDoesNotStarveLaterReports() throws Exception {
        try (var c = owner(); var s = c.prepareStatement("INSERT INTO reports(reporter_id,reported_user_id,reason,status,action_taken,handled_by_admin_id,resolution_note,next_attempt_at) VALUES (?,?,'retry fixture','INVESTIGATING','BAN_USER',?,'Confirmed abuse',now()-interval '1 minute')")) {
            for (int i = 0; i < 55; i++) {
                s.setLong(1, user(c)); s.setLong(2, targetId); s.setLong(3, adminId); s.executeUpdate();
            }
        }
        doThrow(new RuntimeException("permanently unavailable")).when(identity).ban(targetId);
        service.recoverPending();
        verify(identity, times(50)).ban(targetId);
        service.recoverPending();
        verify(identity, times(55)).ban(targetId);
    }

}
