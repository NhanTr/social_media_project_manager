package com.nhantr.social_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.social_service.client.IdentityClient;
import com.nhantr.social_service.client.NotificationClient;
import com.nhantr.social_service.entity.FriendRequest;
import com.nhantr.social_service.repository.BlockRepository;
import com.nhantr.social_service.repository.FriendRequestRepository;
import com.nhantr.social_service.repository.FriendshipRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class SocialServiceConcurrencyTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("social_media_ai_db")
            .withUsername("loccoc")
            .withPassword("loccoc");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void migrateDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        )) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(migration("V1__init_schema.sql")));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(migration("V3__friend_request_cancelled.sql")));
        }
    }

    @Autowired
    private SocialService service;
    @Autowired
    private FriendRequestRepository requests;
    @Autowired
    private FriendshipRepository friendships;
    @Autowired
    private BlockRepository blocks;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private IdentityClient identityClient;
    @MockitoBean
    private NotificationClient notificationClient;

    @BeforeEach
    void resetData() {
        jdbc.execute("TRUNCATE TABLE blocks, friendships, friend_requests, users RESTART IDENTITY CASCADE");
        jdbc.update("""
                INSERT INTO users (id, username, phone_number, auth_provider)
                VALUES (1, 'sender', '+84900000001', 'PHONE'),
                       (2, 'receiver', '+84900000002', 'PHONE')
                """);
        when(identityClient.exists(anyLong())).thenReturn(true);
    }

    @Test
    void concurrentBlockAndAcceptPreserveBlockedNotFriendsNotPendingInvariant() throws Exception {
        FriendRequest request = requests.saveAndFlush(new FriendRequest(1L, 2L));
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> accept = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return transactions.execute(status -> {
                        service.accept(2L, request.getId());
                        return "ACCEPTED";
                    });
                } catch (ApiException exception) {
                    return exception.code();
                }
            });
            Future<Void> block = executor.submit(() -> {
                ready.countDown();
                start.await();
                return transactions.execute(status -> {
                    service.block(1L, 2L);
                    return null;
                });
            });

            ready.await();
            start.countDown();
            assertThat(accept.get()).isIn("ACCEPTED", "FRIEND_REQUEST_CLOSED");
            block.get();
        }

        assertThat(blocks.existsEitherDirection(1L, 2L)).isTrue();
        assertThat(friendships.existsByUserIdAAndUserIdB(1L, 2L)).isFalse();
        assertThat(requests.findPairByStatus(1L, 2L, FriendRequest.Status.PENDING)).isEmpty();
    }

    private static Path migration(String fileName) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path direct = current.resolve("database/migration").resolve(fileName);
            if (Files.exists(direct)) return direct;
            Path nested = current.resolve("backend/database/migration").resolve(fileName);
            if (Files.exists(nested)) return nested;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate migration " + fileName);
    }
}
