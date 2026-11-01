package com.nhantr.notification_service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MockPushNotificationProvider implements PushNotificationProvider {
    private static final Logger log = LoggerFactory.getLogger(MockPushNotificationProvider.class);

    private final AtomicBoolean simulateTransientError = new AtomicBoolean(false);
    private final AtomicInteger sendCount = new AtomicInteger(0);

    @Override
    public PushDeliveryResult send(PushNotificationPayload payload) {
        sendCount.incrementAndGet();
        if (simulateTransientError.get()) {
            log.warn("Mock Push: transient delivery failure simulated for token={}", payload.token());
            return PushDeliveryResult.transientFailure("Service temporarily unavailable (APNs/FCM timeout)");
        }

        String messageId = "push-mock-" + UUID.randomUUID();
        log.info("Mock Push delivered: messageId={} token={} type={} ref={}",
                messageId, payload.token(), payload.type(), payload.referenceId());
        return PushDeliveryResult.delivered(messageId);
    }

    public void setSimulateTransientError(boolean simulate) {
        this.simulateTransientError.set(simulate);
    }

    public int getSendCount() {
        return sendCount.get();
    }

    public void reset() {
        this.simulateTransientError.set(false);
        this.sendCount.set(0);
    }
}

