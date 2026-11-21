package com.nhantr.notification_service.provider;

public interface PushNotificationProvider {
    PushDeliveryResult send(PushNotificationPayload payload);
}

