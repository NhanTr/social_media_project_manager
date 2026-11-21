package com.nhantr.notification_service.provider;

public record PushDeliveryResult(
        boolean success,
        String messageId,
        String errorMessage,
        boolean retryable
) {
    public static PushDeliveryResult delivered(String messageId) {
        return new PushDeliveryResult(true, messageId, null, false);
    }

    public static PushDeliveryResult transientFailure(String errorMessage) {
        return new PushDeliveryResult(false, null, errorMessage, true);
    }

    public static PushDeliveryResult permanentFailure(String errorMessage) {
        return new PushDeliveryResult(false, null, errorMessage, false);
    }
}

