package com.manabihub.payment.event;

import java.util.UUID;

public record PaymentNotificationEvent(
        UUID orderId,
        UUID recipientUserId,
        String recipientEmail,
        String title,
        String message,
        String notificationType,
        String actionUrl
) {
}
