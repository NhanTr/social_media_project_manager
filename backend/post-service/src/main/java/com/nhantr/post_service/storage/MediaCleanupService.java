package com.nhantr.post_service.storage;

import com.nhantr.post_service.entity.MediaCleanupItem;
import com.nhantr.post_service.entity.MediaUpload;
import com.nhantr.post_service.repository.MediaCleanupRepository;
import com.nhantr.post_service.repository.MediaUploadRepository;
import com.nhantr.post_service.repository.PostRepository;
import com.nhantr.post_service.entity.Post;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import java.time.Instant;

/**
 * Hàng đợi dọn media vật lý: object của post đã xóa mềm và upload hết hạn
 * không finalize. Chạy nền trong post-service (không cần worker riêng cho MVP).
 */
@Service
public class MediaCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupService.class);
    private static final int MAX_ATTEMPTS = 5;

    @Value("${retention.media.dry-run:true}")
    private boolean dryRun = true;
    private final MediaCleanupRepository queue;
    private final MediaUploadRepository uploads;
    private final MinioClient minio;
    private final PostRepository posts;
    private final String bucket;
    private final String mediaUrlPrefix;

    public MediaCleanupService(
            MediaCleanupRepository queue,
            MediaUploadRepository uploads,
            PostRepository posts,
            MinioClient minio,
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.public-url}") String publicUrl
    ) {
        this.queue = queue;
        this.uploads = uploads;
        this.minio = minio;
        this.posts = posts;
        this.bucket = bucket;
        this.mediaUrlPrefix = publicUrl.replaceAll("/$", "") + "/" + bucket + "/";
    }

    /** Đẩy object_key suy ra từ mediaUrl vào hàng đợi (idempotent theo nghiệp vụ). */
    public void enqueueFromMediaUrl(String mediaUrl) {
        if (mediaUrl == null || !mediaUrl.startsWith(mediaUrlPrefix)) return;
        enqueue(mediaUrl.substring(mediaUrlPrefix.length()));
    }

    public void enqueue(String objectKey) { enqueue(objectKey, Instant.now()); }

    public void enqueue(String objectKey, Instant scheduledAt) {
        if (objectKey == null || objectKey.isBlank()) return;
        queue.enqueue(objectKey, scheduledAt);
    }

    /** Xóa vật lý các mục chờ (mỗi phút, tối đa 25 mục/lần, retry có backoff). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void processQueue() { processQueue(dryRun); }

    public int processQueue(boolean preview) {
        var items = queue.findTop25ByDoneAtIsNullAndScheduledAtBeforeOrderByScheduledAtAsc(Instant.now());
        if (preview) return items.size();
        for (MediaCleanupItem item : items) {
            if (item.isExhausted(MAX_ATTEMPTS)) continue;
            if (posts.existsRetainedMediaReference(mediaUrlPrefix + item.getObjectKey(), List.of(Post.Status.ACTIVE, Post.Status.HIDDEN))) {
                item.defer();
                queue.save(item);
                continue;
            }
            try {
                minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(item.getObjectKey()).build());
                item.markDone();
            } catch (Exception exception) {
                item.recordFailure("STORAGE_DELETE_FAILED", MAX_ATTEMPTS);
                if (item.isExhausted(MAX_ATTEMPTS)) {
                    log.warn("event=media_cleanup_exhausted");
                }
            }
            queue.save(item);
        }

        return items.size();
    }

    /** Upload session hết hạn mà không finalize -> EXPIRED + dọn object (mỗi 5 phút). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void expireUploads() {
        if (dryRun) return;
        for (MediaUpload upload : uploads.lockExpiredOrphans(List.of(MediaUpload.Status.UPLOADING, MediaUpload.Status.FINALIZED), Instant.now(), org.springframework.data.domain.PageRequest.of(0, 100))) {
            upload.markExpired();
            uploads.save(upload);
            enqueue(upload.getObjectKey());
            // A process may stop after the storage copy but before finalization commits.
            if (!upload.getObjectKey().equals(upload.publishedObjectKey())) enqueue(upload.publishedObjectKey());
        }
    }
}