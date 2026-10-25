package com.nhantr.post_service.service;

import com.nhantr.post_service.client.IdentityClient;
import com.nhantr.post_service.client.NotificationClient;
import com.nhantr.post_service.client.SocialClient;
import com.nhantr.post_service.entity.Post;
import com.nhantr.post_service.storage.MediaStorageService;
import com.nhantr.post_service.storage.MediaCleanupService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real PostgreSQL + MinIO and HTTP object transport; domain-service boundaries for publication. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "LOCCOC_MEDIA_STORAGE_IT", matches = "true")
class MediaLifecycleIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("social_media_ai_db").withUsername("loccoc").withPassword("loccoc");
    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(System.getProperty("loccoc.test.minio.image", "minio/minio:latest"))
            .withEnv("MINIO_ROOT_USER","minioadmin").withEnv("MINIO_ROOT_PASSWORD","minioadmin")
            .withCommand("server /data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static String objectEndpoint() { return "http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000); }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("storage.endpoint",MediaLifecycleIntegrationTests::objectEndpoint);
        registry.add("storage.public-url",MediaLifecycleIntegrationTests::objectEndpoint);
    }
    @BeforeAll
    static void migrate() throws Exception {
        Path directory=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(!Files.isDirectory(directory.resolve("database/migration"))) directory=directory.getParent();
        try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
            var paths=Files.list(directory.resolve("database/migration"))) {
            for(Path file:paths.filter(p -> p.getFileName().toString().matches("V[1-6]__.*\\.sql")).sorted().toList())
                ScriptUtils.executeSqlScript(connection,new FileSystemResource(file));
        }
    }
    @Autowired MediaStorageService storage;
    @Autowired MediaCleanupService cleanup;
    @Autowired PostService posts;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SocialClient social;
    @MockitoBean IdentityClient identity;
    @MockitoBean NotificationClient notifications;

    @Test
    void presignedPutRepeatedFinalizePublishReplayAndConsumedCleanupWithRealStorage() throws Exception {
        jdbc.update("INSERT INTO users(id,username,phone_number,auth_provider) VALUES (1,'owner','+84900000001','PHONE')");
        when(social.friendIds(1L)).thenReturn(List.of(2L));
        when(identity.batch(any())).thenReturn(List.of(new IdentityClient.UserSummary(1L,"owner","Owner",null)));
        var bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(80,60,BufferedImage.TYPE_INT_RGB),"png",bytes);
        byte[] original=bytes.toByteArray();
        var session=storage.createUploadSession(1L,new MediaStorageService.UploadSessionRequest("image/png",(long)original.length));
        try(var http=HttpClient.newHttpClient()) {
            var put=http.send(HttpRequest.newBuilder(URI.create(session.presignedUrl()))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(original)).header("Content-Type","image/png").build(),HttpResponse.BodyHandlers.ofString());
            assertThat(put.statusCode()).isEqualTo(200);
            var media=storage.finalizeUpload(1L,session.uploadId());
            assertThat(storage.finalizeUpload(1L,session.uploadId())).isEqualTo(media);
            assertThat(media.mediaWidth()).isEqualTo(80);
            assertThat(media.mediaHeight()).isEqualTo(60);
            assertThat(media.mediaUrl()).contains("/published/");
            // Even while the original PUT URL works, it cannot overwrite the finalized URL.
            var overwrite=http.send(HttpRequest.newBuilder(URI.create(session.presignedUrl()))
                    .PUT(HttpRequest.BodyPublishers.ofString("different bytes")).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(overwrite.statusCode()).isEqualTo(200);
            var published=http.send(HttpRequest.newBuilder(URI.create(media.mediaUrl())).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertThat(published.body()).isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT scheduled_at > expires_at FROM media_cleanup_queue q CROSS JOIN media_uploads u WHERE q.object_key=?",Boolean.class,session.objectKey())).isTrue();
            UUID key=UUID.randomUUID();
            var request=new PostService.CreatePostRequest(media.mediaType(),media.mediaUrl(),null,media.mediaWidth(),media.mediaHeight(),media.fileSizeBytes(),media.durationSeconds(),"smoke","original",false,null,Post.Visibility.FRIENDS_ONLY,key,session.uploadId());
            var post=posts.create(1L,request);
            assertThat(posts.create(1L,request).id()).isEqualTo(post.id());
            jdbc.update("UPDATE media_uploads SET expires_at=now()-interval '1 hour'");
            cleanup.expireUploads();
            assertThat(jdbc.queryForObject("SELECT consumed_post_id FROM media_uploads WHERE id=?",Long.class,session.uploadId())).isEqualTo(post.id());
            assertThat(jdbc.queryForObject("SELECT status FROM media_uploads WHERE id=?",String.class,session.uploadId())).isEqualTo("FINALIZED");
            verify(notifications,times(1)).newPost(2L,1L,post.id());
            var legacy=new PostService.CreatePostRequest(media.mediaType(),media.mediaUrl(),null,media.mediaWidth(),media.mediaHeight(),media.fileSizeBytes(),media.durationSeconds(),"legacy reference",null,false,null,Post.Visibility.FRIENDS_ONLY,null,null);
            var duplicate=posts.create(1L,legacy);
            posts.delete(1L,duplicate.id());
            cleanup.processQueue();
            var stillReferenced=http.send(HttpRequest.newBuilder(URI.create(media.mediaUrl())).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertThat(stillReferenced.statusCode()).isEqualTo(200);
            assertThat(stillReferenced.body()).isEqualTo(original);
            posts.delete(1L,post.id());
            cleanup.processQueue();
            assertThat(http.send(HttpRequest.newBuilder(URI.create(media.mediaUrl())).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
            assertThat(posts.create(1L,request).status()).isEqualTo("DELETED");
        }
    }
}
