package com.nhantr.notification_service.delivery;

import com.nhantr.notification_service.entity.NotificationDelivery;
import com.nhantr.notification_service.provider.MockPushNotificationProvider;
import com.nhantr.notification_service.provider.PushDeliveryResult;
import com.nhantr.notification_service.provider.PushNotificationPayload;
import com.nhantr.notification_service.provider.PushNotificationProvider;
import com.nhantr.notification_service.repository.NotificationDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class NotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final NotificationDeliveryRepository deliveryRepository;
    private final PushNotificationProvider pushProvider;

    public NotificationDeliveryService(
            NotificationDeliveryRepository deliveryRepository,
            PushNotificationProvider pushProvider
    ) {
        this.deliveryRepository = deliveryRepository;
        this.pushProvider = pushProvider;
    }

    @Transactional
    public NotificationDelivery schedulePushDelivery(Long notificationId, String token, int maxAttempts) {
        NotificationDelivery delivery = new NotificationDelivery(
                notificationId,
                NotificationDelivery.Channel.PUSH,
                token,
                maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS
        );
        return deliveryRepository.save(delivery);
    }

    @Transactional
    public NotificationDelivery processDeliveryAttempt(
            NotificationDelivery delivery,
            PushNotificationPayload payload
    ) {
        if (delivery.getStatus() == NotificationDelivery.Status.EXHAUSTED) {
            log.warn("Delivery id={} is EXHAUSTED. No further retry permitted.", delivery.getId());
            return delivery;
        }

        PushDeliveryResult result = pushProvider.send(payload);
        if (result.success()) {
            delivery.markSuccess();
            log.info("Delivery id={} succeeded on attempt {}", delivery.getId(), delivery.getAttempts() + 1);
        } else {
            // Calculate next backoff: attempts * 5 minutes
            int nextAttempt = delivery.getAttempts() + 1;
            Instant nextRetry = Instant.now().plus(Duration.ofMinutes(nextAttempt * 5L));
            delivery.recordFailure(result.errorMessage(), nextRetry);

            if (delivery.getStatus() == NotificationDelivery.Status.EXHAUSTED) {
                log.warn("Delivery id={} exceeded max_attempts ({}). Marked as EXHAUSTED.",
                        delivery.getId(), delivery.getMaxAttempts());
            } else {
                log.info("Delivery id={} failed on attempt {}. Scheduled retry at {}",
                        delivery.getId(), nextAttempt, nextRetry);
            }
        }
        return deliveryRepository.save(delivery);
    }

    public List<NotificationDelivery> getPendingDeliveries() {
        return deliveryRepository.findByStatusAndScheduledAtBefore(
                NotificationDelivery.Status.FAILED,
                Instant.now()
        );
    }
}

