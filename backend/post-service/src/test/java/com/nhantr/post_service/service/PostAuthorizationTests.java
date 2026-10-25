package com.nhantr.post_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.post_service.client.IdentityClient;
import com.nhantr.post_service.client.NotificationClient;
import com.nhantr.post_service.client.SocialClient;
import com.nhantr.post_service.entity.Post;
import com.nhantr.post_service.repository.PostRepository;
import com.nhantr.post_service.storage.MediaCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostAuthorizationTests {

    private static final Long OWNER = 1L;
    private static final Long VIEWER = 2L;
    private static final String MEDIA_URL = "http://localhost:9000/loccoc-media/users/1/abc.png";

    private PostRepository repository;
    private SocialClient social;
    private MediaCleanupService cleanup;
    private PostService service;

    @BeforeEach
    void setUp() {
        repository = mock(PostRepository.class);
        social = mock(SocialClient.class);
        cleanup = mock(MediaCleanupService.class);
        IdentityClient identity = mock(IdentityClient.class);
        service = new PostService(
                repository,
                social,
                identity,
                mock(NotificationClient.class),
                cleanup,
                mock(com.nhantr.post_service.repository.MediaUploadRepository.class),
                mock(com.nhantr.post_service.repository.PublicationLock.class)
        );
        when(repository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(identity.batch(any()))
                .thenReturn(List.of(new IdentityClient.UserSummary(OWNER, "owner", "Owner", null)));
    }

    private Post friendsOnlyPost() {
        return new Post(
                OWNER, Post.MediaType.IMAGE, MEDIA_URL, null, null, null, null, null,
                "caption", null, false, null, Post.Visibility.FRIENDS_ONLY
        );
    }

    private void stubActivePost(Post post) {
        when(repository.findByIdAndStatus(eq(10L), eq(Post.Status.ACTIVE))).thenReturn(Optional.of(post));
    }

    @Test
    void friendOnlyPostVisibleToFriend() {
        stubActivePost(friendsOnlyPost());
        when(social.areFriends(VIEWER, OWNER)).thenReturn(true);
        when(social.blocked(VIEWER, OWNER)).thenReturn(false);

        PostService.PostResponse response = service.get(VIEWER, 10L);

        assertThat(response.userId()).isEqualTo(OWNER);
        assertThat(response.caption()).isEqualTo("caption");
    }

    @Test
    void friendOnlyPostForbiddenForNonFriend() {
        stubActivePost(friendsOnlyPost());
        when(social.areFriends(VIEWER, OWNER)).thenReturn(false);

        ApiException error = catchThrowableOfType(() -> service.get(VIEWER, 10L), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("POST_FORBIDDEN");
        assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateForbiddenForNonOwner() {
        stubActivePost(friendsOnlyPost());

        ApiException error = catchThrowableOfType(
                () -> service.update(VIEWER, 10L, new PostService.UpdatePostRequest("hacked", null, null)),
                ApiException.class
        );

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("POST_FORBIDDEN");
        verify(repository, never()).save(any(Post.class));
    }

    @Test
    void updateByOwnerChangesCaptionAndVisibility() {
        Post post = friendsOnlyPost();
        stubActivePost(post);

        PostService.PostResponse response =
                service.update(OWNER, 10L, new PostService.UpdatePostRequest("new caption", null, Post.Visibility.PUBLIC));

        assertThat(post.getCaption()).isEqualTo("new caption");
        assertThat(post.getVisibility()).isEqualTo(Post.Visibility.PUBLIC);
        assertThat(response.caption()).isEqualTo("new caption");
    }

    @Test
    void deleteByOwnerSoftDeletesAndEnqueuesMediaCleanup() {
        Post post = friendsOnlyPost();
        stubActivePost(post);

        service.delete(OWNER, 10L);

        assertThat(post.getStatus()).isEqualTo(Post.Status.DELETED);
        assertThat(post.getDeletedAt()).isNotNull();
        verify(cleanup).enqueueFromMediaUrl(MEDIA_URL);
    }

    @Test
    void deleteForbiddenForNonOwner() {
        Post post = friendsOnlyPost();
        stubActivePost(post);

        ApiException error = catchThrowableOfType(() -> service.delete(VIEWER, 10L), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("POST_FORBIDDEN");
        assertThat(post.getStatus()).isEqualTo(Post.Status.ACTIVE);
        verify(cleanup, never()).enqueueFromMediaUrl(any());
    }
}