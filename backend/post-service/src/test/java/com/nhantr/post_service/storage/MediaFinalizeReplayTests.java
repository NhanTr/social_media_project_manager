package com.nhantr.post_service.storage;

import com.nhantr.common.api.ApiException;
import com.nhantr.post_service.entity.MediaUpload;
import com.nhantr.post_service.repository.MediaUploadRepository;
import io.minio.*;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaFinalizeReplayTests {
    @Test
    void finalizedOwnerReplayReturnsSameMetadataWithoutRereadingObject() throws Exception {
        var minio=mock(MinioClient.class);
        var uploads=mock(MediaUploadRepository.class);
        var upload=new MediaUpload(1L,"users/1/uploads/a.png",Instant.now().plusSeconds(600));
        when(uploads.lockById(10L)).thenReturn(Optional.of(upload));
        var stat=mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(200L);
        when(minio.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        var bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100,80,BufferedImage.TYPE_INT_RGB),"png",bytes);
        when(minio.getObject(any(GetObjectArgs.class))).thenReturn(new GetObjectResponse(Headers.of(),"media","us-east-1","a.png",new ByteArrayInputStream(bytes.toByteArray())));
        var cleanup=mock(MediaCleanupService.class);
        var storage=new MediaStorageService(minio,"media","http://localhost:9000","minioadmin","minioadmin",uploads,cleanup);
        var first=storage.finalizeUpload(1L,10L);
        var retry=storage.finalizeUpload(1L,10L);
        assertThat(retry).isEqualTo(first);
        assertThat(first.mediaUrl()).doesNotContain("/uploads/");
        verify(minio,times(1)).copyObject(any(CopyObjectArgs.class));
        verify(cleanup).enqueue("users/1/uploads/a.png", upload.getExpiresAt().plusSeconds(60));
        verify(minio,times(1)).statObject(any(StatObjectArgs.class));
        assertThatThrownBy(() -> storage.finalizeUpload(2L,10L))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("UPLOAD_FORBIDDEN"));
    }
}
