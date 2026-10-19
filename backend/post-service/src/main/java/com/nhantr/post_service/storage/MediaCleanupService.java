package com.nhantr.post_service.storage;

import com.nhantr.post_service.entity.MediaCleanupItem;
import com.nhantr.post_service.entity.MediaUpload;
import com.nhantr.post_service.repository.MediaCleanupRepository;
import com.nhantr.post_service.repository.MediaUploadRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Hàng đợi dọn media vật lý: object của post đã xóa mềm và upload hết hạn
 * không finalize. Chạy nền trong post-service (không cần worker riêng cho MVP).
 */
@Service
public class MediaCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final MediaCleanupRepository queue;
    private final MediaUploadRepository uploads;
    private final MinioClient minio;
    private final String bucket;
    private final String mediaUrlPrefix;

    public MediaCleanupService(
            MediaCleanupRepository queue,
            MediaUploadRepository uploads,
            MinioClient minio,
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.public-url}") String publicUrl
    ) {
        this.queue = queue;
        this.uploads = uploads;
        this.minio = minio;
        this.bucket = bucket;
        this.mediaUrlPrefix = publicUrl.replaceAll("/$", "") + "/" + bucket + "/";
    }

    /** Đẩy object_key suy ra từ mediaUrl vào hàng đợi (idempotent theo nghiệp vụ). */
    public void enqueueFromMediaUrl(String mediaUrl) {
        if (mediaUrl == null || !mediaUrl.contains(mediaUrlPrefix)) return;
        enqueue(mediaUrl.substring(mediaUrl.indexOf(mediaUrlPrefix) + mediaUrlPrefix.length()));
    }

    public void enqueue(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        try {
            queue.save(new MediaCleanupItem(objectKey));
        } catch (DataIntegrityViolationException alreadyQueued) {
            // object_key đã có trong hàng đợi -> không enqueue trùng
        }
    }

    /** Xóa vật lý các mục chờ (mỗi phút, tối đa 25 mục/lần, retry có backoff). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void processQueue() {
        for (MediaCleanupItem item : queue.findTop25ByDoneAtIsNullAndScheduledAtBeforeOrderByScheduledAtAsc(Instant.now())) {
            try {
                minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(item.getObjectKey()).build());
                item.markDone();
            } catch (Exception exception) {
                item.recordFailure(exception.getMessage(), MAX_ATTEMPTS);
                if (item.isExhausted(MAX_ATTEMPTS)) {
                    log.warn("Media cleanup exhausted for {}: {}", item.getObjectKey(), item.getLastError());
                }
            }
            queue.save(item);
        }
    }

    /** Upload session hết hạn mà không finalize -> EXPIRED + dọn object (mỗi 5 phút). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void expireUploads() {
        for (MediaUpload upload : uploads.findByStatusAndExpiresAtBefore(MediaUpload.Status.UPLOADING, Instant.now())) {
            upload.markExpired();
            uploads.save(upload);
            enqueue(upload.getObjectKey());
        }
    }
}