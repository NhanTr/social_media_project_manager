package com.nhantr.post_service.storage;

import com.nhantr.post_service.entity.Post;
import com.nhantr.post_service.entity.MediaUpload;
import com.nhantr.post_service.repository.PostRepository;
import com.nhantr.post_service.repository.MediaUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountMediaRetentionService {
    private final PostRepository posts;
    private final MediaUploadRepository uploads;
    private final MediaCleanupService cleanup;
    private final com.nhantr.post_service.repository.MediaCleanupRepository queue;
    public AccountMediaRetentionService(PostRepository posts, MediaUploadRepository uploads, MediaCleanupService cleanup, com.nhantr.post_service.repository.MediaCleanupRepository queue) {
        this.posts = posts; this.uploads = uploads; this.cleanup = cleanup; this.queue = queue;
    }
    @Transactional
    public Result cleanup(Long userId, boolean dryRun) {
        var page = posts.findTop100ByUserIdAndStatusNotOrderByIdAsc(userId, Post.Status.DELETED);
        var media = uploads.findTop100ByUserIdAndStatusNotOrderByIdAsc(userId, MediaUpload.Status.EXPIRED);
        if (!dryRun) {
            for (var post : page) {
                post.markDeleted(); posts.save(post);
                cleanup.enqueueFromMediaUrl(post.getMediaUrl());
                cleanup.enqueueFromMediaUrl(post.getThumbnailUrl());
            }
            for (var upload : media) {
                upload.markExpired(); uploads.save(upload);
                cleanup.enqueue(upload.getObjectKey());
                cleanup.enqueue(upload.publishedObjectKey());
            }
        }
        return new Result(page.size(), media.size(), page.size() < 100 && media.size() < 100 && !queue.existsByObjectKeyStartingWithAndDoneAtIsNull("users/" + userId + "/"), dryRun);
    }
    public record Result(int posts, int uploads, boolean complete, boolean dryRun) {}
}
