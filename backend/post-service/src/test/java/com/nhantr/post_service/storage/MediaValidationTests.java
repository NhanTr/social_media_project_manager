package com.nhantr.post_service.storage;

import com.nhantr.common.api.ApiException;
import com.nhantr.post_service.repository.MediaUploadRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;

/**
 * Validate MIME/size xảy ra TRƯỚC khi chạm MinIO -> test offline được.
 */
class MediaValidationTests {

    private MediaStorageService storage;

    @BeforeEach
    void setUp() {
        MinioClient minio = MinioClient.builder()
                .endpoint("http://localhost:9000")
                .credentials("minioadmin", "minioadmin")
                .build();
        storage = new MediaStorageService(
                minio,
                "loccoc-media",
                "http://localhost:9000",
                "minioadmin",
                "minioadmin",
                mock(MediaUploadRepository.class),
                mock(MediaCleanupService.class)
        );
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        ApiException error = catchThrowableOfType(() -> storage.upload(1L, empty), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("MEDIA_REQUIRED");
        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsUnsupportedContentType() {
        MockMultipartFile text = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        ApiException error = catchThrowableOfType(() -> storage.upload(1L, text), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("MEDIA_TYPE_UNSUPPORTED");
    }

    @Test
    void rejectsOversizedJpeg() {
        MockMultipartFile oversized = mock(MockMultipartFile.class);
        org.mockito.Mockito.when(oversized.isEmpty()).thenReturn(false);
        org.mockito.Mockito.when(oversized.getContentType()).thenReturn("image/jpeg");
        org.mockito.Mockito.when(oversized.getSize()).thenReturn(11L * 1024 * 1024 + 1);

        ApiException error = catchThrowableOfType(() -> storage.upload(1L, oversized), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("MEDIA_TOO_LARGE");
    }

    @Test
    void presignedSessionRejectsUnsupportedType() {
        MediaStorageService.UploadSessionRequest request =
                new MediaStorageService.UploadSessionRequest("video/mp4", null);

        ApiException error = catchThrowableOfType(() -> storage.createUploadSession(1L, request), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("MEDIA_TYPE_UNSUPPORTED");
    }

    @Test
    void presignedSessionRejectsDeclaredOversizeBeforeSavingSession() {
        MediaStorageService.UploadSessionRequest request =
                new MediaStorageService.UploadSessionRequest("image/jpeg", 11L * 1024 * 1024 + 1);

        ApiException error = catchThrowableOfType(() -> storage.createUploadSession(1L, request), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("MEDIA_TOO_LARGE");
    }
}