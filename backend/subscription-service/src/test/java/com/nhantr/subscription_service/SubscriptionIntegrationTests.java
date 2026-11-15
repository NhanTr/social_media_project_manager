package com.nhantr.subscription_service;

import com.nhantr.common.api.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class SubscriptionIntegrationTests {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("social_media_ai_db").withUsername("postgres").withPassword("test-only-password");
    static final Instant NOW = Instant.parse("2026-11-15T00:00:00Z");
    JdbcTemplate jdbc;
    SubscriptionService service;
    @BeforeAll static void migrate() throws Exception {
        Path dir = Path.of("../database/migration");
        if (!dir.toFile().exists()) dir = Path.of("database/migration");
        try (var c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            ScriptUtils.executeSqlScript(c, new FileSystemResource(dir.resolve("V1__init_schema.sql")));
            ScriptUtils.executeSqlScript(c, new FileSystemResource(dir.resolve("V9__subscription_payment.sql")));
        }
    }
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("TRUNCATE subscription_accounts, subscription_checkouts, subscription_checkout_keys, subscription_events, subscription_audit RESTART IDENTITY CASCADE");
        service = new SubscriptionService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)),
                Clock.fixed(NOW, ZoneOffset.UTC), new SandboxPaymentProvider("https://sandbox.example/api/v1/payments/sandbox/checkouts", "test-webhook-key-with-at-least-32-bytes"));
    }
    @Test void defaultFreeAndAuditedAdminGrantExpires() {
        assertThat(service.snapshot(42L).tier()).isEqualTo("FREE");
        service.override(9L, 42L, "PLUS", NOW.plusSeconds(10), "support correction");
        assertThat(service.entitlements(42L).aiGeneration()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscription_audit WHERE actor_id=9 AND note='support correction'", Integer.class)).isOne();
        assertThatThrownBy(() -> service.override(9L,42L,"PLUS",NOW.plusSeconds(10)," ")).isInstanceOf(ApiException.class);
        assertThat(service.snapshot(42L).validUntil()).isEqualTo(NOW.plusSeconds(10));
    }
    @Test void concurrentCheckoutReusesOnePendingAndReplaySurvivesRestart() throws Exception {
        UUID key = UUID.randomUUID();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> service.checkout(42L,"PLUS",key));
            var b = pool.submit(() -> service.checkout(42L,"PLUS",UUID.randomUUID()));
            assertThat(a.get().sessionId()).isEqualTo(b.get().sessionId());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscription_checkouts", Integer.class)).isOne();
        assertThat(service.checkout(42L,"PLUS",key).sessionId()).isEqualTo(service.snapshot(42L).paymentSessionId());
        assertThatThrownBy(() -> service.checkout(42L,"FREE",key)).isInstanceOf(ApiException.class);
        assertThat(service.snapshot(42L).entitlements().aiGeneration()).isFalse();
    }
    @Test void signedWebhookIsOnlyGrantAndReplayIsAtomic() throws Exception {
        var checkout = service.checkout(42L,"PLUS",UUID.randomUUID());
        var event = event("event-1",checkout.sessionId(),"SUCCEEDED",NOW,NOW.plusSeconds(3600));
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(() -> service.applyEvent(event));
            var b=pool.submit(() -> service.applyEvent(event)); a.get(); b.get();
        }
        assertThat(service.snapshot(42L).entitlements().aiGeneration()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscription_events",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscription_audit",Integer.class)).isOne();
        service.cancel(42L,true);
        assertThat(service.snapshot(42L).cancelAtPeriodEnd()).isTrue();
        assertThat(service.snapshot(42L).entitlements().aiGeneration()).isTrue();
    }
    @Test void refundIsTerminalAndOldSessionCannotReplaceNewPending() {
        var old = service.checkout(42L,"PLUS",UUID.randomUUID());
        service.applyEvent(event("fail",old.sessionId(),"FAILED",NOW,null));
        var newer=service.checkout(42L,"PLUS",UUID.randomUUID());
        service.applyEvent(event("late-success",old.sessionId(),"SUCCEEDED",NOW.plusSeconds(1),NOW.plusSeconds(3600)));
        assertThat(service.snapshot(42L).paymentSessionId()).isEqualTo(newer.sessionId());
        assertThat(service.snapshot(42L).entitlements().aiGeneration()).isFalse();
        service.applyEvent(event("ok",newer.sessionId(),"SUCCEEDED",NOW.plusSeconds(2),NOW.plusSeconds(3600)));
        service.applyEvent(event("refund",newer.sessionId(),"REFUNDED",NOW.plusSeconds(3),null));
        service.applyEvent(event("late-ok",newer.sessionId(),"SUCCEEDED",NOW.plusSeconds(4),NOW.plusSeconds(3600)));
        assertThat(service.snapshot(42L).tier()).isEqualTo("FREE");
    }
    @Test void changedEventIdPayloadConflictsAndUnknownSessionFails() {
        var checkout=service.checkout(42L,"PLUS",UUID.randomUUID());
        service.applyEvent(event("event",checkout.sessionId(),"FAILED",NOW,null));
        assertThatThrownBy(() -> service.applyEvent(event("event",checkout.sessionId(),"SUCCEEDED",NOW,NOW.plusSeconds(60))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.applyEvent(event("missing",UUID.randomUUID().toString(),"SUCCEEDED",NOW,NOW.plusSeconds(60))))
                .isInstanceOf(ApiException.class);
    }
    @Test void refundOfPaidSourceRevokesGrantWithoutClearingNewPending() {
        var paid=service.checkout(42L,"PLUS",UUID.randomUUID());
        service.applyEvent(event("paid",paid.sessionId(),"SUCCEEDED",NOW,NOW.plusSeconds(3600)));
        var pending=service.checkout(42L,"PLUS",UUID.randomUUID());
        service.applyEvent(event("refund-paid",paid.sessionId(),"REFUNDED",NOW.plusSeconds(1),null));
        assertThat(service.snapshot(42L).entitlements().aiGeneration()).isFalse();
        assertThat(service.snapshot(42L).paymentSessionId()).isEqualTo(pending.sessionId());
        assertThat(service.snapshot(42L).paymentStatus()).isEqualTo("PENDING");
    }
    @Test void failedRenewalKeepsPaidPeriodAsGraceWithoutExtendingIt() {
        var paid=service.checkout(42L,"PLUS",UUID.randomUUID());
        service.applyEvent(event("paid-grace",paid.sessionId(),"SUCCEEDED",NOW,NOW.plusSeconds(3600)));
        var renewal=service.checkout(42L,"PLUS",UUID.randomUUID());
        service.applyEvent(event("failed-renewal",renewal.sessionId(),"FAILED",NOW.plusSeconds(1),null));
        assertThat(service.snapshot(42L).status()).isEqualTo("GRACE");
        assertThat(service.snapshot(42L).entitlements().aiGeneration()).isTrue();
        assertThat(service.snapshot(42L).currentPeriodEnd()).isEqualTo(NOW.plusSeconds(3600));
    }
    @Test void eventReplayUsesCanonicalDatabasePrecision() {
        var paid=service.checkout(42L,"PLUS",UUID.randomUUID());
        var event=event("nano",paid.sessionId(),"SUCCEEDED",NOW.plusNanos(123456789),NOW.plusSeconds(3600).plusNanos(123456789));
        assertThat(service.applyEvent(event)).isTrue();
        assertThat(service.applyEvent(event)).isFalse();
    }
    @Test void refundOfUnpaidNewCheckoutCannotRevokeAnOlderPaidPeriod() {
        var paid=service.checkout(42L,"PLUS",UUID.randomUUID());
        service.applyEvent(event("paid-stays",paid.sessionId(),"SUCCEEDED",NOW,NOW.plusSeconds(3600)));
        var unpaid=service.checkout(42L,"PLUS",UUID.randomUUID());
        service.applyEvent(event("refund-unpaid",unpaid.sessionId(),"REFUNDED",NOW.plusSeconds(1),null));
        assertThat(service.snapshot(42L).entitlements().aiGeneration()).isTrue();
        assertThat(service.snapshot(42L).currentPeriodEnd()).isEqualTo(NOW.plusSeconds(3600));
    }
    @Test void rawSignedWebhookAndHostedSandboxExerciseActualGrantBoundary() {
        var provider=new SandboxPaymentProvider("https://sandbox.example/checkouts","test-webhook-key-with-at-least-32-bytes");
        var clock=Clock.fixed(NOW,ZoneOffset.UTC);
        var mapper=new tools.jackson.databind.ObjectMapper();
        var controller=new SubscriptionController(service,provider,clock,mapper);
        var hosted=new SandboxCheckoutController(service,provider,clock,mapper,controller);
        var session=service.checkout(42L,"PLUS",UUID.randomUUID());
        assertThat(hosted.sandbox(session.sessionId()).getBody()).contains("Sandbox");
        assertThat(service.entitlements(42L).aiGeneration()).isFalse();
        hosted.completeSandbox(session.sessionId(),"SUCCEEDED");
        assertThat(service.entitlements(42L).aiGeneration()).isTrue();
        hosted.completeSandbox(session.sessionId(),"SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscription_events",Integer.class)).isOne();
        String body=mapper.writeValueAsString(event("refund-http",session.sessionId(),"REFUNDED",NOW.plusSeconds(1),null));
        assertThatThrownBy(() -> controller.webhook(body,"bad")).isInstanceOf(ApiException.class);
        assertThat(service.entitlements(42L).aiGeneration()).isTrue();
        controller.webhook(body,provider.sign(body,NOW));
        assertThat(service.entitlements(42L).aiGeneration()).isFalse();
        assertThat(controller.me(new com.nhantr.common.security.CurrentUser(42L,"USER",true)).getHeaders().getCacheControl()).isEqualTo("no-store");
    }
    @Test void disabledSandboxCannotCreateCheckoutOrAdvertisePurchases() {
        var ds=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        var disabled=new SubscriptionService(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),
                Clock.fixed(NOW,ZoneOffset.UTC),new SandboxPaymentProvider("https://sandbox.example/checkouts","test-webhook-key-with-at-least-32-bytes",false));
        assertThat(disabled.plans().stream().filter(p -> p.tier().equals("PLUS")).findFirst().orElseThrow().checkoutEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.checkout(42L,"PLUS",UUID.randomUUID())).isInstanceOf(ApiException.class);
    }
    @Test void hostedCompletionRejectsExpiredCheckoutUsingWallClock() {
        var provider=new SandboxPaymentProvider("https://sandbox.example/checkouts","test-webhook-key-with-at-least-32-bytes");
        var lateClock=Clock.fixed(NOW.plusSeconds(1801),ZoneOffset.UTC);
        var mapper=new tools.jackson.databind.ObjectMapper();
        var controller=new SubscriptionController(service,provider,lateClock,mapper);
        var hosted=new SandboxCheckoutController(service,provider,lateClock,mapper,controller);
        var session=service.checkout(42L,"PLUS",UUID.randomUUID());
        assertThat(hosted.completeSandbox(session.sessionId(),"SUCCEEDED").getStatusCode().value()).isEqualTo(410);
        assertThat(service.entitlements(42L).aiGeneration()).isFalse();
    }
    private SubscriptionService.PaymentEvent event(String id,String session,String type,Instant at,Instant end) {
        return new SubscriptionService.PaymentEvent(id,session,type,at,end);
    }
}
