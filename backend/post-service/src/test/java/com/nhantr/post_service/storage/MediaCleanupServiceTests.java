package com.nhantr.post_service.storage;

import com.nhantr.post_service.entity.MediaCleanupItem;
import com.nhantr.post_service.repository.MediaCleanupRepository;
import com.nhantr.post_service.repository.MediaUploadRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MediaCleanupServiceTests {

    private MediaCleanupRepository queue;
    private MediaCleanupService service;

    @BeforeEach
    void setUp() {
        queue = mock(MediaCleanupRepository.class);
        MinioClient minio = MinioClient.builder()
                .endpoint("http://localhost:9000")
                .credentials("minioadmin", "minioadmin")
                .build();
        service = new MediaCleanupService(
                queue,
                mock(MediaUploadRepository.class),
                minio,
                "loccoc-media",
                "http://localhost:9000"
        );
    }

    @Test
    void enqueueFromMediaUrlExtractsObjectKey() {
        service.enqueueFromMediaUrl("http://localhost:9000/loccoc-media/users/1/2026/abc.png");

        ArgumentCaptor<MediaCleanupItem> captor = ArgumentCaptor.forClass(MediaCleanupItem.class);
        verify(queue).save(captor.capture());
        assertThat(captor.getValue().getObjectKey()).isEqualTo("users/1/2026/abc.png");
        assertThat(captor.getValue().getAttempts()).isZero();
    }

    @Test
    void enqueueFromMediaUrlIgnoresForeignUrl() {
        service.enqueueFromMediaUrl("https://other-cdn.example.com/whatever.png");

        verify(queue, org.mockito.Mockito.never()).save(any(MediaCleanupItem.class));
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