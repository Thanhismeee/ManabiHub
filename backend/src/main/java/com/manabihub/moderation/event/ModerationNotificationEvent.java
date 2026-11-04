package com.manabihub.moderation.event;

import java.util.UUID;

public record ModerationNotificationEvent(
        UUID recipientUserId,
        String recipientEmail,
        String title,
        String message,
        String notificationType
) {
}
