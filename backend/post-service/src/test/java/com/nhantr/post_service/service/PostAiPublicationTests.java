package com.nhantr.post_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.post_service.client.AiClient;
import com.nhantr.post_service.client.IdentityClient;
import com.nhantr.post_service.client.NotificationClient;
import com.nhantr.post_service.client.SocialClient;
import com.nhantr.post_service.entity.Post;
import com.nhantr.post_service.repository.MediaUploadRepository;
import com.nhantr.post_service.repository.PostRepository;
import com.nhantr.post_service.repository.PublicationLock;
import com.nhantr.post_service.storage.MediaCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostAiPublicationTests {
    private PostRepository repository;
    private SocialClient social;
    private IdentityClient identity;
    private NotificationClient notifications;
    private MediaCleanupService cleanup;
    private MediaUploadRepository uploads;
    private PublicationLock publicationLock;
    private AiClient aiClient;
    private PostService service;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        repository = mock(PostRepository.class);
        social = mock(SocialClient.class);
        identity = mock(IdentityClient.class);
        notifications = mock(NotificationClient.class);
        cleanup = mock(MediaCleanupService.class);
        uploads = mock(MediaUploadRepository.class);
        publicationLock = mock(PublicationLock.class);
        aiClient = mock(AiClient.class);

        service = new PostService(
                repository,
                social,
                identity,
                notifications,
                cleanup,
                uploads,
                publicationLock,
                aiClient
        );

        when(repository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            return p;
        });
        when(identity.batch(any()))
                .thenReturn(List.of(new IdentityClient.UserSummary(USER_ID, "testuser", "Test User", null)));
        when(social.friendIds(USER_ID)).thenReturn(List.of());
    }

    @Test
    void publishAiPostSuccessWithWidgetThumbnail() {
        Long aiGenId = 100L;
        String generatedUrl = "https://cdn.loccoc.com/media/ai-100.jpg";
        String widgetThumbUrl = "https://cdn.loccoc.com/media/ai-100-widget.jpg";
        String originalUrl = "https://cdn.loccoc.com/media/orig-100.jpg";

        when(aiClient.verify(eq(USER_ID), eq(aiGenId))).thenReturn(
                new AiClient.VerifyResponse(true, generatedUrl, widgetThumbUrl, originalUrl, null)
        );

        var request = new PostService.CreatePostRequest(
                Post.MediaType.IMAGE,
                generatedUrl,
                null,
                1024,
                1024,
                200000L,
                null,
                "Look at this AI generated photo!",
                "Cyberpunk",
                true,
                aiGenId,
                Post.Visibility.FRIENDS_ONLY,
                null,
                null
        );

        PostService.PostResponse response = service.create(USER_ID, request);
        assertThat(response).isNotNull();
        assertThat(response.aiGenerated()).isTrue();
        assertThat(response.aiGenerationId()).isEqualTo(aiGenId);
        assertThat(response.thumbnailUrl()).isEqualTo(widgetThumbUrl);
    }

    @Test
    void publishAiPostFailsIfAiGenerationIdMissing() {
        var request = new PostService.CreatePostRequest(
                Post.MediaType.IMAGE,
                "https://cdn.loccoc.com/media/some-pic.jpg",
                null,
                null,
                null,
                null,
                null,
                "AI caption",
                null,
                true,
                null, // Missing ID
                Post.Visibility.FRIENDS_ONLY,
                null,
                null
        );

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(400);
                    assertThat(apiEx.getMessage()).contains("AI generated posts must specify aiGenerationId");
                });
    }

    @Test
    void publishAiPostFailsIfVerificationInvalid() {
        Long aiGenId = 101L;
        when(aiClient.verify(eq(USER_ID), eq(aiGenId))).thenReturn(
                new AiClient.VerifyResponse(false, null, null, null, "AI generation is not completed successfully.")
        );

        var request = new PostService.CreatePostRequest(
                Post.MediaType.IMAGE,
                "https://cdn.loccoc.com/media/failed-ai.jpg",
                null,
                null,
                null,
                null,
                null,
                "Attempting to publish uncompleted AI",
                null,
                true,
                aiGenId,
                Post.Visibility.FRIENDS_ONLY,
                null,
                null
        );

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(400);
                    assertThat(apiEx.getMessage()).contains("AI generation is not completed successfully");
                });
    }

    @Test
    void publishAiPostFailsIfMediaUrlMismatch() {
        Long aiGenId = 102L;
        when(aiClient.verify(eq(USER_ID), eq(aiGenId))).thenReturn(
                new AiClient.VerifyResponse(true, "https://cdn.loccoc.com/real-ai.jpg", "https://cdn.loccoc.com/thumb.jpg", "https://cdn.loccoc.com/orig.jpg", null)
        );

        var request = new PostService.CreatePostRequest(
                Post.MediaType.IMAGE,
                "https://cdn.loccoc.com/fake-different-image.jpg",
                null,
                null,
                null,
                null,
                null,
                "Tampered media url",
                null,
                true,
                aiGenId,
                Post.Visibility.FRIENDS_ONLY,
                null,
                null
        );

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(400);
                    assertThat(apiEx.getMessage()).contains("Post mediaUrl does not match AI generation result");
                });
    }

    @Test
    void fallbackToOriginalMediaWhenAiFailedIsAllowed() {
        Long aiGenId = 103L;
        String originalUrl = "https://cdn.loccoc.com/original-photo.jpg";
        when(aiClient.verify(eq(USER_ID), eq(aiGenId))).thenReturn(
                new AiClient.VerifyResponse(true, "https://cdn.loccoc.com/ai-gen-103.jpg", "https://cdn.loccoc.com/widget-103.jpg", originalUrl, null)
        );

        // Client chooses to fallback to originalUrl
        var request = new PostService.CreatePostRequest(
                Post.MediaType.IMAGE,
                originalUrl,
                null,
                null,
                null,
                null,
                null,
                "Fallback to my original camera photo",
                null,
                true,
                aiGenId,
                Post.Visibility.FRIENDS_ONLY,
                null,
                null
        );

        PostService.PostResponse resp = service.create(USER_ID, request);
        assertThat(resp).isNotNull();
        assertThat(resp.mediaUrl()).isEqualTo(originalUrl);
    }
}
