package com.nhantr.post_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.common.api.PageResponse;
import com.nhantr.post_service.client.IdentityClient;
import com.nhantr.post_service.client.NotificationClient;
import com.nhantr.post_service.client.SocialClient;
import com.nhantr.post_service.entity.Post;
import com.nhantr.post_service.entity.MediaUpload;
import com.nhantr.post_service.repository.MediaUploadRepository;
import com.nhantr.post_service.repository.PublicationLock;
import com.nhantr.post_service.repository.PostRepository;
import com.nhantr.post_service.storage.MediaCleanupService;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.security.MessageDigest;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PostService {
    private final PostRepository repository;
    private final SocialClient social;
    private final IdentityClient identity;
    private final NotificationClient notifications;
    private final MediaCleanupService cleanup;
    private final MediaUploadRepository uploads;
    private final PublicationLock publicationLock;

    public PostService(
            PostRepository repository,
            SocialClient social,
            IdentityClient identity,
            NotificationClient notifications,
            MediaCleanupService cleanup,
            MediaUploadRepository uploads,
            PublicationLock publicationLock
    ) {
        this.repository = repository;
        this.social = social;
        this.identity = identity;
        this.notifications = notifications;
        this.cleanup = cleanup;
        this.uploads = uploads;
        this.publicationLock = publicationLock;
    }

    @Transactional
    public PostResponse create(Long userId, CreatePostRequest request) {
        if ((request.clientRequestId() == null) != (request.uploadId() == null)) {
            throw ApiException.badRequest("POST_UPLOAD_BINDING_REQUIRED", "clientRequestId and uploadId must be provided together.");
        }
        String fingerprint = null;
        MediaUpload upload = null;
        if (request.clientRequestId() != null) {
            publicationLock.acquire(userId);
            fingerprint = fingerprint(request);
            Optional<Post> replay = repository.findByUserIdAndClientRequestId(userId, request.clientRequestId());
            if (replay.isPresent()) {
                if (!fingerprint.equals(replay.get().getRequestFingerprint())) {
                    throw ApiException.conflict("POST_REQUEST_CONFLICT", "The publication key was already used with another payload.");
                }
                return response(replay.get(), authorMap(List.of(userId)));
            }
            upload = uploads.lockById(request.uploadId())
                    .orElseThrow(() -> ApiException.notFound("UPLOAD_SESSION_NOT_FOUND", "Upload session not found."));
            if (!userId.equals(upload.getUserId())) {
                throw ApiException.forbidden("UPLOAD_FORBIDDEN", "Upload session belongs to another user.");
            }
            if (upload.getStatus() != MediaUpload.Status.FINALIZED || upload.getFileSizeBytes() == null) {
                throw ApiException.conflict("UPLOAD_NOT_FINALIZED", "Finalize this upload before publishing.");
            }
            if (upload.getConsumedPostId() != null) {
                throw ApiException.conflict("UPLOAD_ALREADY_CONSUMED", "This upload was already published.");
            }
            if (!Objects.equals(request.mediaType().name(), upload.getMediaType())
                    || !Objects.equals(request.mediaUrl(), upload.getMediaUrl())
                    || !Objects.equals(request.fileSizeBytes(), upload.getFileSizeBytes())
                    || !Objects.equals(request.mediaWidth(), upload.getMediaWidth())
                    || !Objects.equals(request.mediaHeight(), upload.getMediaHeight())
                    || !Objects.equals(request.durationSeconds(), upload.getDurationSeconds())
                    || request.thumbnailUrl() != null) {
                throw ApiException.badRequest("UPLOAD_METADATA_MISMATCH", "Media metadata must match the finalized upload.");
            }
        }
        if (request.mediaType() == Post.MediaType.GIF
                && request.durationSeconds() != null
                && request.durationSeconds() > 5) {
            throw ApiException.badRequest("GIF_TOO_LONG", "GIF duration must not exceed 5 seconds.");
        }
        Post post = new Post(
                userId,
                request.mediaType(),
                request.mediaUrl(),
                request.thumbnailUrl(),
                request.mediaWidth(),
                request.mediaHeight(),
                request.fileSizeBytes(),
                request.durationSeconds(),
                normalize(request.caption()),
                normalize(request.filterApplied()),
                Boolean.TRUE.equals(request.aiGenerated()),
                request.aiGenerationId(),
                request.visibility()
        );
        if (upload != null) post.bindPublication(request.clientRequestId(), fingerprint, upload.getId());
        post = repository.save(post);
        if (upload != null) upload.consume(post.getId());
        List<Long> friendIds = social.friendIds(userId);
        Long postId = post.getId();
        Runnable fanOut = () -> friendIds.forEach(friendId -> notifications.newPost(friendId, userId, postId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { fanOut.run(); }
            });
        } else {
            fanOut.run();
        }
        return response(post, authorMap(List.of(userId)));
    }

    private String fingerprint(CreatePostRequest request) {
        try {
            byte[] payload = JsonMapper.builder().build().writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    public PostResponse get(Long currentUserId, Long postId) {
        Post post = active(postId);
        authorizeView(currentUserId, post);
        return response(post, authorMap(List.of(post.getUserId())));
    }

    @Transactional
    public PostResponse update(Long currentUserId, Long postId, UpdatePostRequest request) {
        Post post = active(postId);
        requireOwner(currentUserId, post, "Only the owner can update this post.");
        if (request.caption() != null) post.setCaption(normalize(request.caption()));
        if (request.filterApplied() != null) post.setFilterApplied(normalize(request.filterApplied()));
        if (request.visibility() != null) post.setVisibility(request.visibility());
        return response(repository.save(post), authorMap(List.of(post.getUserId())));
    }

    /**
     * Soft delete: ẩn ngay khỏi feed (status=DELETED) và lên lịch xóa media vật lý.
     */
    @Transactional
    public void delete(Long currentUserId, Long postId) {
        Post post = active(postId);
        requireOwner(currentUserId, post, "Only the owner can delete this post.");
        post.markDeleted();
        repository.save(post);
        cleanup.enqueueFromMediaUrl(post.getMediaUrl());
    }

    private void requireOwner(Long currentUserId, Post post, String message) {
        if (!currentUserId.equals(post.getUserId())) {
            throw ApiException.forbidden("POST_FORBIDDEN", message);
        }
    }

    public PageResponse<PostResponse> feed(Long userId, String cursor, int requestedLimit) {
        List<Long> friendIds = social.friendIds(userId);
        if (friendIds.isEmpty()) return PageResponse.of(List.of());
        Cursor parsed = Cursor.parse(cursor);
        int limit = safeLimit(requestedLimit);
        List<Post> rows = feedRows(friendIds, parsed, limit);
        return page(rows, limit);
    }

    public PageResponse<PostResponse> feedByFriend(Long userId, Long friendId, String cursor, int requestedLimit) {
        if (!social.areFriends(userId, friendId) || social.blocked(userId, friendId)) {
            throw ApiException.forbidden("NOT_FRIENDS", "Feed can only be filtered by an active friend.");
        }
        Cursor parsed = Cursor.parse(cursor);
        int limit = safeLimit(requestedLimit);
        List<Post> rows = feedRows(List.of(friendId), parsed, limit);
        return page(rows, limit);
    }

    public PageResponse<PostResponse> postsByUser(Long currentUserId, Long targetUserId, String cursor, int requestedLimit) {
        if (!currentUserId.equals(targetUserId)
                && (!social.areFriends(currentUserId, targetUserId) || social.blocked(currentUserId, targetUserId))) {
            throw ApiException.forbidden("POSTS_FORBIDDEN", "User posts are only visible to friends.");
        }
        Cursor parsed = Cursor.parse(cursor);
        int limit = safeLimit(requestedLimit);
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Post> rows = parsed.time() == null
                ? repository.byUser(targetUserId, Post.Status.ACTIVE, page)
                : repository.byUserAfter(targetUserId, Post.Status.ACTIVE, parsed.time(), parsed.id(), page);
        return page(rows, limit);
    }

    private List<Post> feedRows(List<Long> userIds, Cursor cursor, int limit) {
        PageRequest page = PageRequest.of(0, limit + 1);
        return cursor.time() == null
                ? repository.feed(userIds, Post.Visibility.FRIENDS_ONLY, Post.Status.ACTIVE, page)
                : repository.feedAfter(
                        userIds, Post.Visibility.FRIENDS_ONLY, Post.Status.ACTIVE,
                        cursor.time(), cursor.id(), page
                );
    }

    public WidgetResponse latestWidgetPost(Long userId) {
        PageResponse<PostResponse> feed = feed(userId, null, 1);
        if (feed.items().isEmpty()) {
            throw ApiException.notFound("WIDGET_POST_NOT_FOUND", "No friend post is available.");
        }
        PostResponse post = feed.items().getFirst();
        return new WidgetResponse(
                post.id(), post.mediaType(), post.mediaUrl(), post.thumbnailUrl(),
                post.caption(), post.author() == null ? null : post.author().displayName(), post.createdAt()
        );
    }

    @Transactional
    public PostResponse changeStatus(Long postId, Post.Status status) {
        Post post = repository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("POST_NOT_FOUND", "Post not found."));
        post.setStatus(status);
        return response(repository.save(post), authorMap(List.of(post.getUserId())));
    }

    public PostResponse internalGet(Long postId) {
        Post post = repository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("POST_NOT_FOUND", "Post not found."));
        return response(post, authorMap(List.of(post.getUserId())));
    }

    private PageResponse<PostResponse> page(List<Post> rows, int limit) {
        boolean hasNext = rows.size() > limit;
        List<Post> selected = hasNext ? rows.subList(0, limit) : rows;
        Map<Long, IdentityClient.UserSummary> authors = authorMap(selected.stream().map(Post::getUserId).distinct().toList());
        List<PostResponse> responses = selected.stream().map(post -> response(post, authors)).toList();
        String next = hasNext && !selected.isEmpty() ? Cursor.of(selected.getLast()).encode() : null;
        return new PageResponse<>(responses, next, hasNext);
    }

    private Map<Long, IdentityClient.UserSummary> authorMap(List<Long> ids) {
        return identity.batch(ids).stream().collect(Collectors.toMap(IdentityClient.UserSummary::id, Function.identity()));
    }

    private void authorizeView(Long currentUserId, Post post) {
        if (currentUserId.equals(post.getUserId())) return;
        if (post.getVisibility() == Post.Visibility.PUBLIC && !social.blocked(currentUserId, post.getUserId())) return;
        if (post.getVisibility() == Post.Visibility.FRIENDS_ONLY
                && social.areFriends(currentUserId, post.getUserId())
                && !social.blocked(currentUserId, post.getUserId())) return;
        throw ApiException.forbidden("POST_FORBIDDEN", "Post is not visible to this user.");
    }

    private Post active(Long id) {
        return repository.findByIdAndStatus(id, Post.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("POST_NOT_FOUND", "Post not found."));
    }

    private int safeLimit(int limit) { return Math.max(1, Math.min(limit, 50)); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private PostResponse response(Post post, Map<Long, IdentityClient.UserSummary> authors) {
        return new PostResponse(
                post.getId(), post.getUserId(), authors.get(post.getUserId()), post.getMediaType().name(),
                post.getMediaUrl(), post.getThumbnailUrl(), post.getMediaWidth(), post.getMediaHeight(),
                post.getFileSizeBytes(), post.getDurationSeconds(), post.getCaption(), post.getFilterApplied(),
                post.isAiGenerated(), post.getAiGenerationId(), post.getVisibility().name(), post.getStatus().name(),
                post.getCreatedAt()
        );
    }

    public record CreatePostRequest(
            @NotNull Post.MediaType mediaType,
            @NotBlank @Size(max = 2048) String mediaUrl,
            @Size(max = 2048) String thumbnailUrl,
            @Positive Integer mediaWidth,
            @Positive Integer mediaHeight,
            @PositiveOrZero Long fileSizeBytes,
            @Min(0) @Max(5) Integer durationSeconds,
            @Size(max = 2000) String caption,
            @Size(max = 100) String filterApplied,
            Boolean aiGenerated,
            Long aiGenerationId,
            Post.Visibility visibility,
            UUID clientRequestId,
            @Positive Long uploadId
    ) {}

    /** Field null nghĩa là giữ nguyên giá trị hiện tại. */
    public record UpdatePostRequest(
            @Size(max = 2000) String caption,
            @Size(max = 100) String filterApplied,
            Post.Visibility visibility
    ) {}

    public record PostResponse(
            Long id, Long userId, IdentityClient.UserSummary author, String mediaType, String mediaUrl,
            String thumbnailUrl, Integer mediaWidth, Integer mediaHeight, Long fileSizeBytes,
            Integer durationSeconds, String caption, String filterApplied, boolean aiGenerated,
            Long aiGenerationId, String visibility, String status, Instant createdAt
    ) {}

    public record WidgetResponse(
            Long postId, String mediaType, String mediaUrl, String thumbnailUrl,
            String caption, String authorName, Instant createdAt
    ) {}

    private record Cursor(Instant time, Long id) {
        static Cursor parse(String value) {
            if (value == null || value.isBlank()) return new Cursor(null, Long.MAX_VALUE);
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(value));
                if (decoded.startsWith("v2|")) {
                    String[] parts = decoded.split("\\|", -1);
                    if (parts.length != 3) throw new IllegalArgumentException("Invalid cursor fields");
                    return new Cursor(Instant.parse(parts[1]), Long.parseLong(parts[2]));
                }
                // Continue accepting cursors emitted by older service versions.
                String[] parts = decoded.split(":", 2);
                return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
            } catch (RuntimeException exception) {
                throw ApiException.badRequest("CURSOR_INVALID", "Cursor is invalid.");
            }
        }
        static Cursor of(Post post) { return new Cursor(post.getCreatedAt(), post.getId()); }
        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(("v2|" + time + "|" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
