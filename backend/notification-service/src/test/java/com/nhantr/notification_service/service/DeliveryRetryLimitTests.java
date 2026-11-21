package com.nhantr.notification_service.service;

import com.nhantr.notification_service.delivery.NotificationDeliveryService;
import com.nhantr.notification_service.entity.NotificationDelivery;
import com.nhantr.notification_service.provider.MockPushNotificationProvider;
import com.nhantr.notification_service.provider.PushNotificationPayload;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeNotificationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryRetryLimitTests {

    private NotificationDeliveryService deliveryService;
    private MockPushNotificationProvider pushProvider;

    @BeforeEach
    void setUp() {
        pushProvider = new MockPushNotificationProvider();
        var deliveryRepo = new FakeNotificationDeliveryRepository();
        deliveryService = new NotificationDeliveryService(deliveryRepo, pushProvider);
    }

    @Test
    void deliveryFailureRetriesUpToMaxAttemptsAndTransitionsToExhausted() {
        pushProvider.setSimulateTransientError(true);

        PushNotificationPayload payload = PushNotificationPayload.of(
                "device-token-abc", "New Post", "Friend posted photo", "NEW_POST", 888L
        );

        // Schedule delivery with max 3 attempts
        NotificationDelivery delivery = deliveryService.schedulePushDelivery(555L, "device-token-abc", 3);
        assertThat(delivery.getStatus()).isEqualTo(NotificationDelivery.Status.PENDING);
        assertThat(delivery.getAttempts()).isEqualTo(0);

        // Attempt 1: fails
        delivery = deliveryService.processDeliveryAttempt(delivery, payload);
        assertThat(delivery.getStatus()).isEqualTo(NotificationDelivery.Status.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastError()).contains("temporarily unavailable");

        // Attempt 2: fails
        delivery = deliveryService.processDeliveryAttempt(delivery, payload);
        assertThat(delivery.getStatus()).isEqualTo(NotificationDelivery.Status.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(2);

        // Attempt 3: fails -> transitions to EXHAUSTED
        delivery = deliveryService.processDeliveryAttempt(delivery, payload);
        assertThat(delivery.getStatus()).isEqualTo(NotificationDelivery.Status.EXHAUSTED);
        assertThat(delivery.getAttempts()).isEqualTo(3);

        // Attempt 4: does not re-invoke push provider; stays EXHAUSTED
        int countBefore = pushProvider.getSendCount();
        delivery = deliveryService.processDeliveryAttempt(delivery, payload);
        assertThat(delivery.getStatus()).isEqualTo(NotificationDelivery.Status.EXHAUSTED);
        assertThat(pushProvider.getSendCount()).isEqualTo(countBefore);
    }

    @Test
    void deliverySuccessMarksStatusSent() {
        pushProvider.setSimulateTransientError(false);

        PushNotificationPayload payload = PushNotificationPayload.of(
                "device-token-abc", "New Post", "Friend posted photo", "NEW_POST", 888L
        );

        NotificationDelivery delivery = deliveryService.schedulePushDelivery(555L, "device-token-abc", 3);
        delivery = deliveryService.processDeliveryAttempt(delivery, payload);

        assertThat(delivery.getStatus()).isEqualTo(NotificationDelivery.Status.SENT);
        assertThat(delivery.getDeliveredAt()).isNotNull();
    }
}

