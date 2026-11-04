package com.manabihub.moderation.event;

import com.manabihub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterModerationCommit(ModerationNotificationEvent event) {
        try {
            notificationService.createNotification(
                    event.recipientUserId(),
                    event.recipientEmail(),
                    event.title(),
                    event.message(),
                    event.notificationType()
            );
        } catch (RuntimeException exception) {
            // Moderation is already committed. A notification transport failure
            // must never undo the decision or its enforcement actions.
            log.error(
                    "Unable to deliver moderation notification type={} to user={}",
                    event.notificationType(),
                    event.recipientUserId(),
                    exception
            );
        }
    }
}
