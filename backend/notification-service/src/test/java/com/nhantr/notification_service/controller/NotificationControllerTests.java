package com.nhantr.notification_service.controller;

import com.nhantr.common.security.CurrentUser;
import com.nhantr.notification_service.delivery.NotificationDeliveryService;
import com.nhantr.notification_service.email.EmailTemplateService;
import com.nhantr.notification_service.email.MockEmailAdapter;
import com.nhantr.notification_service.entity.DeviceToken;
import com.nhantr.notification_service.entity.Notification;
import com.nhantr.notification_service.provider.MockPushNotificationProvider;
import com.nhantr.notification_service.service.NotificationService;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeDeviceTokenRepository;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeNotificationDeliveryRepository;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationControllerTests {

    private NotificationController controller;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        var notificationRepo = new FakeNotificationRepository();
        var tokenRepo = new FakeDeviceTokenRepository();
        var pushProvider = new MockPushNotificationProvider();
        var deliveryRepo = new FakeNotificationDeliveryRepository();
        var deliveryService = new NotificationDeliveryService(deliveryRepo, pushProvider);
        var emailAdapter = new MockEmailAdapter(new EmailTemplateService());

        service = new NotificationService(notificationRepo, tokenRepo, pushProvider, emailAdapter, deliveryService);
        controller = new NotificationController(service);
    }

    @Test
    void unreadCountEndpointReturnsCorrectCount() {
        CurrentUser user = new CurrentUser(100L, "USER", true);

        assertThat(controller.unreadCount(user).count()).isEqualTo(0);

        service.create(Notification.Type.NEW_POST, new NotificationService.NotificationEvent(
                100L, 102L, 77L, "Title", "Body"
        ), "key-1");

        assertThat(controller.unreadCount(user).count()).isEqualTo(1);
    }

    @Test
    void registerAndUnregisterDeviceTokenEndpoints() {
        CurrentUser user = new CurrentUser(100L, "USER", true);

        var reg = controller.register(user, new NotificationService.DeviceTokenRequest(
                "token-abc-123", DeviceToken.Platform.IOS, null
        ));
        assertThat(reg.active()).isTrue();
        assertThat(reg.token()).isEqualTo("token-abc-123");

        controller.unregister(user, "token-abc-123");
    }

    @Test
    void readAllEndpointResetsUnreadCount() {
        CurrentUser user = new CurrentUser(100L, "USER", true);

        service.create(Notification.Type.NEW_POST, new NotificationService.NotificationEvent(
                100L, 102L, 77L, "Title 1", "Body 1"
        ), "key-1");
        service.create(Notification.Type.NEW_POST, new NotificationService.NotificationEvent(
                100L, 102L, 78L, "Title 2", "Body 2"
        ), "key-2");

        var response = controller.readAll(user);
        assertThat(response.updated()).isEqualTo(2);
        assertThat(controller.unreadCount(user).count()).isEqualTo(0);
    }
}

