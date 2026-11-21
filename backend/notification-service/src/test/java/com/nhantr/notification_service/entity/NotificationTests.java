package com.nhantr.notification_service.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTests {
    @Test
    void markReadChangesReadState() {
        Notification notification = new Notification(1L, 2L, Notification.Type.NEW_POST, 10L, "Title", "Body", "key");
        notification.markRead();
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
    }
}
