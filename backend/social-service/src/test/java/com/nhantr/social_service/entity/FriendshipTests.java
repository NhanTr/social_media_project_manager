package com.nhantr.social_service.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendshipTests {
    @Test
    void storesPairInCanonicalOrder() {
        Friendship friendship = new Friendship(20L, 10L);
        assertThat(friendship.getUserIdA()).isEqualTo(10L);
        assertThat(friendship.getUserIdB()).isEqualTo(20L);
        assertThat(friendship.other(10L)).isEqualTo(20L);
    }
}
