package com.nhantr.chat_service.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTests {
    @Test
    void normalizesParticipantsAndFindsOtherUser() {
        Conversation conversation = new Conversation(9L, 3L);
        assertThat(conversation.getUserId1()).isEqualTo(3L);
        assertThat(conversation.getUserId2()).isEqualTo(9L);
        assertThat(conversation.other(3L)).isEqualTo(9L);
    }
}
