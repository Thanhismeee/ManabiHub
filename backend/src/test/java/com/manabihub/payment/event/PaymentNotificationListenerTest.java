package com.manabihub.payment.event;

import com.manabihub.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @Test
    void sendsIdempotentNotificationAfterPaymentCommit() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PaymentNotificationEvent event = new PaymentNotificationEvent(
                orderId,
                userId,
                "student@test.dev",
                "Payment succeeded",
                "Order paid",
                "PURCHASE_SUCCESS",
                "/student/courses");

        new PaymentNotificationListener(notificationService).sendAfterPaymentCommit(event);

        verify(notificationService).createNotificationOnce(
                "payment:" + orderId + ":" + userId + ":PURCHASE_SUCCESS",
                userId,
                "student@test.dev",
                "Payment succeeded",
                "Order paid",
                "PURCHASE_SUCCESS",
                "/student/courses");
    }

    @Test
    void notificationFailureCannotEscapeAfterCommitListener() {
        PaymentNotificationEvent event = new PaymentNotificationEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "student@test.dev",
                "Payment succeeded",
                "Order paid",
                "PURCHASE_SUCCESS",
                "/student/courses");
        doThrow(new IllegalStateException("notification unavailable"))
                .when(notificationService)
                .createNotificationOnce(
                        "payment:" + event.orderId() + ":" + event.recipientUserId() + ":PURCHASE_SUCCESS",
                        event.recipientUserId(),
                        event.recipientEmail(),
                        event.title(),
                        event.message(),
                        event.notificationType(),
                        event.actionUrl());

        assertDoesNotThrow(() ->
                new PaymentNotificationListener(notificationService).sendAfterPaymentCommit(event));
    }
}
