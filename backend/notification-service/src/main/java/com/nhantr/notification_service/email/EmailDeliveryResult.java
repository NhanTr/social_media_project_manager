package com.nhantr.notification_service.email;

public record EmailDeliveryResult(
        boolean success,
        String messageId,
        String errorMessage,
        boolean retryable
) {
    public static EmailDeliveryResult delivered(String messageId) {
        return new EmailDeliveryResult(true, messageId, null, false);
    }

    public static EmailDeliveryResult transientFailure(String errorMessage) {
        return new EmailDeliveryResult(false, null, errorMessage, true);
    }

    public static EmailDeliveryResult permanentFailure(String errorMessage) {
        return new EmailDeliveryResult(false, null, errorMessage, false);
    }
}

