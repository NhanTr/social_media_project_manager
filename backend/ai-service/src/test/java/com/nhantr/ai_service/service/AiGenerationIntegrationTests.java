package com.nhantr.ai_service.service;

import com.nhantr.ai_service.client.SubscriptionClient;
import com.nhantr.ai_service.entity.AiGeneration;
import com.nhantr.ai_service.repository.AiGenerationRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false",
        "ai.worker.poll-ms=3600000", "ai.worker.timeout-ms=50"})
@Testcontainers(disabledWithoutDocker = true)
class AiGenerationIntegrationTests {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("social_media_ai_db").withUsername("loccoc").withPassword("loccoc");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @BeforeAll static void migrate() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var paths = Files.list(migrations())) {
            for (Path file : paths.filter(p -> p.getFileName().toString().matches("V([1-8]|10)__.*\\.sql"))
                    .sorted(Comparator.comparingInt(p -> Integer.parseInt(p.getFileName().toString().split("__")[0].substring(1)))).toList()) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(file));
            }
        }
    }
    @Autowired AiGenerationService service;
    @Autowired AiGenerationWorker worker;
    @Autowired AiGenerationRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean SubscriptionClient subscription;
    @BeforeEach void data() {
        jdbc.execute("TRUNCATE ai_generations, users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id, username, phone_number, auth_provider) VALUES (7,'seven','+84900000007','PHONE')");
    }
    @Test void committedPendingRowFinishesAndExpiredLeaseRecoversWithNoSecondCharge() {
        var created = service.create(7L, request("anime"));
        assertThat(created.status()).isEqualTo("PENDING");
        worker.poll();
        assertThat(service.get(7L, created.id()).status()).isEqualTo("SUCCESS");
        jdbc.update("UPDATE ai_generations SET status='PROCESSING', lease_until=now()-interval '1 minute', attempt_token=null WHERE id=?", created.id());
        worker.poll();
        var job = repository.findById(created.id()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(AiGeneration.Status.SUCCESS);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getMockChargeCount()).isOne();
    }
    @Test void timeoutPersistsFallbackAndRetryKeepsOriginalJobAndCharge() {
        var created = service.create(7L, request("[timeout]"));
        worker.poll();
        var failed = service.get(7L, created.id());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.errorMessage()).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(failed.originalMediaUrl()).isEqualTo("https://media.test/a.png");
        assertThat(failed.generatedMediaUrl()).isNull();
        assertThat(service.retry(7L, created.id()).id()).isEqualTo(created.id());
        worker.poll();
        assertThat(repository.findById(created.id()).orElseThrow().getMockChargeCount()).isOne();
        assertThat(repository.count()).isOne();
    }
    @Test void rolledBackCreateNeverBecomesWorkerInput() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> { service.create(7L, request("anime")); tx.setRollbackOnly(); });
        worker.poll();
        assertThat(repository.count()).isZero();
    }
    @Test void simultaneousWorkersClaimOnce() throws Exception {
        var created=service.create(7L, request("anime"));
        CountDownLatch start=new CountDownLatch(1);
        try (var threads=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=threads.submit(() -> { start.await(); worker.poll(); return true; });
            var second=threads.submit(() -> { start.await(); worker.poll(); return true; });
            start.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        }
        var job=repository.findById(created.id()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(AiGeneration.Status.SUCCESS);
        assertThat(job.getAttemptCount()).isOne();
        assertThat(job.getMockChargeCount()).isOne();
    }
    private AiGenerationService.CreateGenerationRequest request(String prompt) {
        return new AiGenerationService.CreateGenerationRequest("https://media.test/a.png", prompt, AiGeneration.Type.IMAGE_TO_IMAGE);
    }
    private static Path migrations() {
        Path path=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path!=null) { if (Files.isDirectory(path.resolve("database/migration"))) return path.resolve("database/migration"); path=path.getParent(); }
        throw new IllegalStateException("Migration directory not found");
    }
}
