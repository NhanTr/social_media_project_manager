package com.nhantr.post_service.storage;

import com.nhantr.post_service.entity.MediaCleanupItem;
import com.nhantr.post_service.repository.MediaCleanupRepository;
import com.nhantr.post_service.repository.MediaUploadRepository;
import com.nhantr.post_service.repository.PostRepository;
import java.time.Instant;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MediaCleanupServiceTests {

    private MediaCleanupRepository queue;
    private MediaCleanupService service;
    private PostRepository posts;
    private MinioClient minio;

    @BeforeEach
    void setUp() {
        queue = mock(MediaCleanupRepository.class);
        minio = mock(MinioClient.class);
        posts = mock(PostRepository.class);
        service = new MediaCleanupService(
                queue,
                mock(MediaUploadRepository.class),
                posts,
                minio,
                "loccoc-media",
                "http://localhost:9000"
        );
    }

    @Test
    void activeThumbnailReferencePreventsPhysicalDeletion() {
        var item = new MediaCleanupItem("users/1/thumbnail.png");
        org.mockito.Mockito.when(queue.findTop25ByDoneAtIsNullAndScheduledAtBeforeOrderByScheduledAtAsc(any()))
                .thenReturn(java.util.List.of(item));
        org.mockito.Mockito.when(posts.existsRetainedMediaReference(org.mockito.ArgumentMatchers.eq("http://localhost:9000/loccoc-media/users/1/thumbnail.png"), any()))
                .thenReturn(true);
        service.processQueue(false);
        org.mockito.Mockito.verifyNoInteractions(minio);
        assertThat(item.getDoneAt()).isNull();
    }

    @Test
    void dryRunDoesNotMutateQueueOrDeleteStorage() {
        var item = new MediaCleanupItem("users/1/x.png");
        org.mockito.Mockito.when(queue.findTop25ByDoneAtIsNullAndScheduledAtBeforeOrderByScheduledAtAsc(any()))
                .thenReturn(java.util.List.of(item));
        assertThat(service.processQueue(true)).isEqualTo(1);
        verify(queue, org.mockito.Mockito.never()).save(any());
        assertThat(item.getAttempts()).isZero();
    }

    @Test
    void enqueueFromMediaUrlExtractsObjectKey() {
        service.enqueueFromMediaUrl("http://localhost:9000/loccoc-media/users/1/2026/abc.png");

        verify(queue).enqueue(org.mockito.ArgumentMatchers.eq("users/1/2026/abc.png"), any(Instant.class));
    }

    @Test
    void enqueueFromMediaUrlIgnoresForeignUrl() {
        service.enqueueFromMediaUrl("https://other-cdn.example.com/whatever.png");

        verify(queue, org.mockito.Mockito.never()).enqueue(any(), any());
    }

    @Test
    void failureRecordsBackoffAndStopsAtMaxAttempts() {
        MediaCleanupItem item = new MediaCleanupItem("users/1/x.png");

        item.recordFailure("boom", 5);
        assertThat(item.getAttempts()).isEqualTo(1);
        assertThat(item.getLastError()).isEqualTo("boom");
        assertThat(item.isExhausted(5)).isFalse();

        for (int i = 0; i < 4; i++) item.recordFailure("boom", 5);
        assertThat(item.getAttempts()).isEqualTo(5);
        assertThat(item.isExhausted(5)).isTrue();
    }
}