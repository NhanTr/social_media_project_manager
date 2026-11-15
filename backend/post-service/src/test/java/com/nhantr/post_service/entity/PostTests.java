package com.nhantr.post_service.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostTests {
    @Test
    void moderationHideReplayPreservesDeletedState() {
        Post post = new Post(1L, Post.MediaType.IMAGE, "http://media/test.jpg", null,
                100, 100, 10L, null, null, null, false, null, null);
        post.markDeleted();
        var deletedAt = post.getDeletedAt();
        post.setStatus(Post.Status.HIDDEN);
        assertThat(post.getStatus()).isEqualTo(Post.Status.DELETED);
        assertThat(post.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void defaultsToFriendsOnlyAndActive() {
        Post post = new Post(
                1L, Post.MediaType.IMAGE, "http://media/test.jpg", null,
                100, 100, 10L, null, null, null, false, null, null
        );
        assertThat(post.getVisibility()).isEqualTo(Post.Visibility.FRIENDS_ONLY);
        assertThat(post.getStatus()).isEqualTo(Post.Status.ACTIVE);
    }
}
