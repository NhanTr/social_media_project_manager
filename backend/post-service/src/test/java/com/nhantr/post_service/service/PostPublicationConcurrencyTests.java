package com.nhantr.post_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.post_service.client.IdentityClient;
import com.nhantr.post_service.client.NotificationClient;
import com.nhantr.post_service.client.SocialClient;
import com.nhantr.post_service.entity.Post;
import com.nhantr.post_service.storage.MediaStorageService;
import com.nhantr.post_service.storage.MediaCleanupService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
class PostPublicationConcurrencyTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("social_media_ai_db").withUsername("loccoc").withPassword("loccoc");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void migrate() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var paths = Files.list(migrations())) {
                for (Path file : paths.filter(p -> p.getFileName().toString().matches("V[1-6]__.*\\.sql")).sorted().toList()) {
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource(file));
                }
            }
        }
    }

    @Autowired PostService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MediaCleanupService cleanup;
    @MockitoBean SocialClient social;
    @MockitoBean IdentityClient identity;
    @MockitoBean NotificationClient notifications;
    @MockitoBean MediaStorageService storage;

    @BeforeEach
    void data() {
        jdbc.execute("TRUNCATE media_cleanup_queue, media_uploads, posts, users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id, username, phone_number, auth_provider) VALUES (1,'one','+84900000001','PHONE'),(2,'two','+84900000002','PHONE')");
        jdbc.update("INSERT INTO media_uploads(id,user_id,object_key,status,media_type,expires_at) VALUES (10,1,'users/1/uploads/a.jpg','FINALIZED','IMAGE',now()+interval '1 hour')");
        jdbc.update("UPDATE media_uploads SET media_url='http://localhost:9000/loccoc-media/users/1/uploads/a.jpg',file_size_bytes=123,media_width=100,media_height=80 WHERE id=10");
        when(social.friendIds(anyLong())).thenReturn(List.of(2L));
        when(identity.batch(any())).thenReturn(List.of(new IdentityClient.UserSummary(1L,"one","One",null)));
    }

    private PostService.CreatePostRequest request(UUID key, Long uploadId, String caption) {
        return new PostService.CreatePostRequest(Post.MediaType.IMAGE,"http://localhost:9000/loccoc-media/users/1/uploads/a.jpg",null,100,80,123L,null,caption,"original",false,null,Post.Visibility.FRIENDS_ONLY,key,uploadId);
    }

    @Test
    void identicalRetryReturnsOriginalPostEvenAfterDeletionAndOnlyNotifiesOnce() {
        UUID key = UUID.randomUUID();
        var first = service.create(1L,request(key,10L,"caption"));
        service.delete(1L,first.id());
        var retry = service.create(1L,request(key,10L,"caption"));
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(retry.status()).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posts",Integer.class)).isOne();
        verify(notifications,times(1)).newPost(2L,1L,first.id());
    }

    @Test
    void changedPayloadWithSameKeyConflicts() {
        UUID key = UUID.randomUUID();
        service.create(1L,request(key,10L,"first"));
        assertThatThrownBy(() -> service.create(1L,request(key,10L,"changed")))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("POST_REQUEST_CONFLICT"));
    }

    @Test
    void concurrentIdenticalRequestsCreateExactlyOnePostAndNotification() throws Exception {
        UUID key=UUID.randomUUID();
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        try (var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=executor.submit(() -> { ready.countDown(); start.await(); return service.create(1L,request(key,10L,"same")); });
            var b=executor.submit(() -> { ready.countDown(); start.await(); return service.create(1L,request(key,10L,"same")); });
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            var first=a.get(20,TimeUnit.SECONDS); var second=b.get(20,TimeUnit.SECONDS);
            assertThat(first.id()).isEqualTo(second.id());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM posts",Integer.class)).isOne();
            verify(notifications,times(1)).newPost(2L,1L,first.id());
        }
    }

    @Test
    void rejectsForeignUnfinalizedAndMissingBindingsAndKeepsLegacyCaller() {
        assertThatThrownBy(() -> service.create(2L,request(UUID.randomUUID(),10L,"caption")))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("UPLOAD_FORBIDDEN"));
        jdbc.update("UPDATE media_uploads SET status='UPLOADING' WHERE id=10");
        assertThatThrownBy(() -> service.create(1L,request(UUID.randomUUID(),10L,"caption")))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("UPLOAD_NOT_FINALIZED"));
        assertThatThrownBy(() -> service.create(1L,request(UUID.randomUUID(),null,"caption")))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("POST_UPLOAD_BINDING_REQUIRED"));
        assertThatThrownBy(() -> service.create(1L,request(null,10L,"caption")))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("POST_UPLOAD_BINDING_REQUIRED"));
        assertThat(service.create(1L,request(null,null,"legacy")).caption()).isEqualTo("legacy");
    }


    @Test
    void abandonedFinalizedMediaExpiresButConsumedMediaSurvivesOrphanCleanup() {
        service.create(1L,request(UUID.randomUUID(),10L,"caption"));
        jdbc.update("UPDATE media_uploads SET expires_at=now()-interval '1 hour' WHERE id=10");
        jdbc.update("INSERT INTO media_uploads(id,user_id,object_key,status,media_type,expires_at) VALUES (11,1,'users/1/uploads/orphan.jpg','FINALIZED','IMAGE',now()-interval '1 hour')");
        cleanup.expireUploads();
        assertThat(jdbc.queryForObject("SELECT status FROM media_uploads WHERE id=10",String.class)).isEqualTo("FINALIZED");
        assertThat(jdbc.queryForObject("SELECT status FROM media_uploads WHERE id=11",String.class)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForList("SELECT object_key FROM media_cleanup_queue",String.class)).containsExactlyInAnyOrder("users/1/uploads/orphan.jpg","users/1/published/orphan.jpg");
    }

    @Test
    void rejectsSpoofedMetadataAndUploadReuseUnderDifferentPublicationKey() {
        var valid=request(UUID.randomUUID(),10L,"caption");
        var spoofed=new PostService.CreatePostRequest(valid.mediaType(),valid.mediaUrl(),null,999,80,123L,null,valid.caption(),valid.filterApplied(),false,null,valid.visibility(),valid.clientRequestId(),10L);
        assertThatThrownBy(() -> service.create(1L,spoofed))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("UPLOAD_METADATA_MISMATCH"));
        var post=service.create(1L,valid);
        assertThatThrownBy(() -> service.create(1L,request(UUID.randomUUID(),10L,"caption")))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("UPLOAD_ALREADY_CONSUMED"));
        jdbc.update("UPDATE posts SET status='HIDDEN' WHERE id=?",post.id());
        assertThat(service.create(1L,valid).status()).isEqualTo("HIDDEN");
    }

    @Test
    void publicationUuidIsScopedToAuthenticatedAccount() {
        UUID key=UUID.randomUUID();
        var one=service.create(1L,request(key,10L,"caption"));
        jdbc.update("INSERT INTO media_uploads(id,user_id,object_key,status,media_type,expires_at,media_url,file_size_bytes,media_width,media_height) VALUES (20,2,'users/2/published/a.jpg','FINALIZED','IMAGE',now()+interval '1 hour','http://localhost:9000/loccoc-media/users/2/published/a.jpg',123,100,80)");
        var other=new PostService.CreatePostRequest(Post.MediaType.IMAGE,"http://localhost:9000/loccoc-media/users/2/published/a.jpg",null,100,80,123L,null,"caption","original",false,null,Post.Visibility.FRIENDS_ONLY,key,20L);
        var two=service.create(2L,other);
        assertThat(two.id()).isNotEqualTo(one.id());
        assertThat(two.userId()).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posts",Integer.class)).isEqualTo(2);
    }

    @Test
    void cursorPreservesMicrosecondOrderingAcrossPagesWithinOneMillisecond() {
        jdbc.update("INSERT INTO posts(user_id,media_type,media_url,visibility,created_at) VALUES (2,'IMAGE','http://media/early.jpg','FRIENDS_ONLY','2026-10-25T10:00:00.123100Z'),(2,'IMAGE','http://media/middle.jpg','FRIENDS_ONLY','2026-10-25T10:00:00.123200Z'),(2,'IMAGE','http://media/late.jpg','FRIENDS_ONLY','2026-10-25T10:00:00.123300Z')");
        var first=service.feed(1L,null,1);
        var second=service.feed(1L,first.nextCursor(),1);
        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().getFirst().mediaUrl()).isEqualTo("http://media/middle.jpg");
        var third=service.feed(1L,second.nextCursor(),1);
        assertThat(third.items()).hasSize(1);
        assertThat(third.items().getFirst().mediaUrl()).isEqualTo("http://media/early.jpg");
        assertThat(third.hasNext()).isFalse();
    }

    private static Path migrations() {
        Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p!=null) { if(Files.isDirectory(p.resolve("database/migration"))) return p.resolve("database/migration"); p=p.getParent(); }
        throw new IllegalStateException("Migration directory not found");
    }
}
