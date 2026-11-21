package com.nhantr.notification_service.service;

import com.nhantr.notification_service.delivery.NotificationDeliveryService;
import com.nhantr.notification_service.email.EmailTemplateService;
import com.nhantr.notification_service.email.MockEmailAdapter;
import com.nhantr.notification_service.entity.Notification;
import com.nhantr.notification_service.provider.MockPushNotificationProvider;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeDeviceTokenRepository;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeNotificationDeliveryRepository;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationInboxTests {

    private NotificationService service;
    private FakeNotificationRepository notificationRepo;
    private FakeDeviceTokenRepository tokenRepo;

    @BeforeEach
    void setUp() {
        notificationRepo = new FakeNotificationRepository();
        tokenRepo = new FakeDeviceTokenRepository();
        MockPushNotificationProvider pushProvider = new MockPushNotificationProvider();
        FakeNotificationDeliveryRepository deliveryRepo = new FakeNotificationDeliveryRepository();
        NotificationDeliveryService deliveryService = new NotificationDeliveryService(deliveryRepo, pushProvider);
        MockEmailAdapter emailAdapter = new MockEmailAdapter(new EmailTemplateService());

        service = new NotificationService(notificationRepo, tokenRepo, pushProvider, emailAdapter, deliveryService);
    }

    @Test
    void inboxUnreadCountAndMarkReadWorkflow() {
        Long userId = 101L;

        // Create 3 notifications
        service.create(Notification.Type.NEW_POST, new NotificationService.NotificationEvent(
                userId, 102L, 501L, "New Photo", "Tuấn Dũng sent a photo to your Widget"), "key-1");
        service.create(Notification.Type.FRIEND_REQUEST, new NotificationService.NotificationEvent(
                userId, 103L, null, "Friend Request", "Khánh Linh wants to connect"), "key-2");
        var n3 = service.create(Notification.Type.CHAT_MESSAGE, new NotificationService.NotificationEvent(
                userId, 102L, 901L, "New Message", "Hey there!"), "key-3");

        // Unread count should be 3
        assertThat(service.getUnreadCount(userId)).isEqualTo(3);

        // Mark 1 as read
        var readResult = service.markRead(userId, n3.id());
        assertThat(readResult.read()).isTrue();
        assertThat(readResult.readAt()).isNotNull();

        // Unread count should now be 2
        assertThat(service.getUnreadCount(userId)).isEqualTo(2);

        // Mark all as read
        int updatedCount = service.markAllRead(userId);
        assertThat(updatedCount).isEqualTo(2);

        // Unread count should now be 0
        assertThat(service.getUnreadCount(userId)).isEqualTo(0);
    }

    @Test
    void listInboxReturnsPagedResultsOrderedDescending() {
        Long userId = 101L;

        service.create(Notification.Type.NEW_POST, new NotificationService.NotificationEvent(
                userId, 102L, 501L, "Photo 1", "Body 1"), "k-1");
        service.create(Notification.Type.NEW_POST, new NotificationService.NotificationEvent(
                userId, 102L, 502L, "Photo 2", "Body 2"), "k-2");

        var page = service.list(userId, null, 10);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).title()).isEqualTo("Photo 2");
    }
}

