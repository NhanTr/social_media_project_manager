package com.nhantr.notification_service.service;

import com.nhantr.notification_service.delivery.NotificationDeliveryService;
import com.nhantr.notification_service.email.EmailTemplateService;
import com.nhantr.notification_service.email.MockEmailAdapter;
import com.nhantr.notification_service.entity.DeviceToken;
import com.nhantr.notification_service.entity.Notification;
import com.nhantr.notification_service.provider.MockPushNotificationProvider;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeDeviceTokenRepository;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeNotificationDeliveryRepository;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationIdempotencyTests {

    private NotificationService service;
    private FakeNotificationRepository notificationRepo;
    private MockPushNotificationProvider pushProvider;

    @BeforeEach
    void setUp() {
        notificationRepo = new FakeNotificationRepository();
        var tokenRepo = new FakeDeviceTokenRepository();
        pushProvider = new MockPushNotificationProvider();
        var deliveryRepo = new FakeNotificationDeliveryRepository();
        var deliveryService = new NotificationDeliveryService(deliveryRepo, pushProvider);
        var emailAdapter = new MockEmailAdapter(new EmailTemplateService());

        // Register one active device token
        tokenRepo.save(new DeviceToken(101L, "device-token-101", DeviceToken.Platform.IOS));

        service = new NotificationService(notificationRepo, tokenRepo, pushProvider, emailAdapter, deliveryService);
    }

    @Test
    void duplicateIdempotencyKeyDoesNotCreateDuplicateNotificationOrSendDuplicatePush() {
        Long userId = 101L;
        String idempotencyKey = "post-pub-idempotency-key-777";

        NotificationService.NotificationEvent event = new NotificationService.NotificationEvent(
                userId, 102L, 999L, "Widget Photo", "A friend sent a new photo"
        );

        // First call
        var first = service.create(Notification.Type.NEW_POST, event, idempotencyKey);
        assertThat(first).isNotNull();
        assertThat(notificationRepo.count()).isEqualTo(1);
        assertThat(pushProvider.getSendCount()).isEqualTo(1);

        // Second call with the same idempotency key (e.g. mobile network retry)
        var second = service.create(Notification.Type.NEW_POST, event, idempotencyKey);
        assertThat(second).isNotNull();
        assertThat(second.id()).isEqualTo(first.id());

        // Still only 1 notification in repository
        assertThat(notificationRepo.count()).isEqualTo(1);

        // Push provider must NOT be called a second time
        assertThat(pushProvider.getSendCount()).isEqualTo(1);
    }
}

