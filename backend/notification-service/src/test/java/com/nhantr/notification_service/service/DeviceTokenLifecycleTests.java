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

class DeviceTokenLifecycleTests {

    private NotificationService service;
    private FakeDeviceTokenRepository tokenRepo;
    private MockPushNotificationProvider pushProvider;

    @BeforeEach
    void setUp() {
        var notificationRepo = new FakeNotificationRepository();
        tokenRepo = new FakeDeviceTokenRepository();
        pushProvider = new MockPushNotificationProvider();
        var deliveryRepo = new FakeNotificationDeliveryRepository();
        var deliveryService = new NotificationDeliveryService(deliveryRepo, pushProvider);
        var emailAdapter = new MockEmailAdapter(new EmailTemplateService());

        service = new NotificationService(notificationRepo, tokenRepo, pushProvider, emailAdapter, deliveryService);
    }

    @Test
    void registerAndRotateDeviceToken() {
        Long userId = 200L;

        // Register initial iOS token
        var t1 = service.register(userId, new NotificationService.DeviceTokenRequest(
                "apns-token-initial-111", DeviceToken.Platform.IOS, null
        ));
        assertThat(t1.active()).isTrue();
        assertThat(tokenRepo.findByUserIdAndActiveTrue(userId)).hasSize(1);

        // Rotate token with new APNs token and oldToken provided
        var t2 = service.register(userId, new NotificationService.DeviceTokenRequest(
                "apns-token-refreshed-222", DeviceToken.Platform.IOS, "apns-token-initial-111"
        ));
        assertThat(t2.active()).isTrue();

        // Old token should now be deactivated
        var old = tokenRepo.findByToken("apns-token-initial-111").orElseThrow();
        assertThat(old.isActive()).isFalse();

        // Only the new token remains active
        var activeTokens = tokenRepo.findByUserIdAndActiveTrue(userId);
        assertThat(activeTokens).hasSize(1);
        assertThat(activeTokens.get(0).getToken()).isEqualTo("apns-token-refreshed-222");
    }

    @Test
    void logoutDisablesAllTokensSoSubsequentEventsDoNotPush() {
        Long userId = 201L;

        // User logs in on phone and tablet
        service.register(userId, new NotificationService.DeviceTokenRequest("device-iphone", DeviceToken.Platform.IOS, null));
        service.register(userId, new NotificationService.DeviceTokenRequest("device-ipad", DeviceToken.Platform.IOS, null));
        assertThat(tokenRepo.findByUserIdAndActiveTrue(userId)).hasSize(2);

        // Logout: disable all tokens for this user
        int disabledCount = service.disableAllForUser(userId);
        assertThat(disabledCount).isEqualTo(2);
        assertThat(tokenRepo.findByUserIdAndActiveTrue(userId)).isEmpty();

        // Now a friend posts a photo to user's widget
        pushProvider.reset();
        service.create(Notification.Type.NEW_POST, new NotificationService.NotificationEvent(
                userId, 102L, 501L, "New Locket Photo", "Tuấn Dũng posted to widget"
        ), "post-event-after-logout");

        // Push provider should NOT be called for disabled tokens!
        assertThat(pushProvider.getSendCount()).isEqualTo(0);
    }
}

