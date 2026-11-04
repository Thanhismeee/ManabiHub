package com.manabihub.payment.event;

import com.manabihub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAfterPaymentCommit(PaymentNotificationEvent event) {
        try {
            notificationService.createNotificationOnce(
                    dedupeKey(event),
                    event.recipientUserId(),
                    event.recipientEmail(),
                    event.title(),
                    event.message(),
                    event.notificationType(),
                    event.actionUrl());
        } catch (RuntimeException exception) {
            log.error(
                    "Unable to deliver payment notification type={} for order={}",
                    event.notificationType(),
                    event.orderId(),
                    exception);
        }
    }

    private String dedupeKey(PaymentNotificationEvent event) {
        return "payment:" + event.orderId() + ":" + event.recipientUserId()
                + ":" + event.notificationType();
    }
}
