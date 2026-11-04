package com.manabihub.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private String title;
    private String message;
    private String notificationType;
    private String actionUrl;
    private boolean read;
    private Instant createdAt;
    private Instant readAt;
}
