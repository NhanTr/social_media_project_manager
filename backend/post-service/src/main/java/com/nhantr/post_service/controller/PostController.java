package com.nhantr.post_service.controller;

import com.nhantr.common.api.PageResponse;
import com.nhantr.common.security.CurrentUser;
import com.nhantr.post_service.service.PostService;
import com.nhantr.post_service.storage.MediaStorageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PostController {
    private final PostService posts;
    private final MediaStorageService storage;

    public PostController(PostService posts, MediaStorageService storage) {
        this.posts = posts;
        this.storage = storage;
    }

    @PostMapping(value = "/api/v1/media/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaStorageService.UploadResponse upload(
            @AuthenticationPrincipal CurrentUser user,
            @RequestPart("file") MultipartFile file
    ) {
        return storage.upload(user.id(), file);
    }

    @PostMapping("/api/v1/media/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaStorageService.UploadSessionResponse createUploadSession(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody MediaStorageService.UploadSessionRequest request
    ) {
        return storage.createUploadSession(user.id(), request);
    }

    @PostMapping("/api/v1/media/uploads/{uploadId}/finalize")
    public MediaStorageService.UploadResponse finalizeUpload(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long uploadId
    ) {
        return storage.finalizeUpload(user.id(), uploadId);
    }

    @PostMapping("/api/v1/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostService.PostResponse create(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody PostService.CreatePostRequest request
    ) {
        return posts.create(user.id(), request);
    }

    @PutMapping("/api/v1/posts/{id}")
    public PostService.PostResponse update(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id,
            @Valid @RequestBody PostService.UpdatePostRequest request
    ) {
        return posts.update(user.id(), id, request);
    }

    @DeleteMapping("/api/v1/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        posts.delete(user.id(), id);
    }

    @GetMapping("/api/v1/posts/{id}")
    public PostService.PostResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return posts.get(user.id(), id);
    }

    @GetMapping("/api/v1/feed")
    public PageResponse<PostService.PostResponse> feed(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return posts.feed(user.id(), cursor, limit);
    }

    @GetMapping("/api/v1/feed/by-friend/{friendId}")
    public PageResponse<PostService.PostResponse> byFriend(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long friendId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return posts.feedByFriend(user.id(), friendId, cursor, limit);
    }

    @GetMapping("/api/v1/posts/users/{userId}")
    public PageResponse<PostService.PostResponse> byUser(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return posts.postsByUser(user.id(), userId, cursor, limit);
    }

    @GetMapping("/api/v1/widget/latest-post")
    public PostService.WidgetResponse widget(@AuthenticationPrincipal CurrentUser user) {
        return posts.latestWidgetPost(user.id());
    }
}
