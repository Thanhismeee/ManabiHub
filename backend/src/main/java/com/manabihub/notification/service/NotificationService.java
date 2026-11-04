package com.manabihub.notification.service;

import com.manabihub.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    Page<NotificationResponse> listMyNotifications(UUID userId, String type,
                                                   Boolean isRead, Pageable pageable);

    long countUnread(UUID userId);

    NotificationResponse markAsRead(UUID notificationId, UUID userId);

    NotificationResponse markAsUnread(UUID notificationId, UUID userId);

    int markAllAsRead(UUID userId);

    /**
     * Broadcasts a notification to all users holding a specific role.
     * Uses batch insert under the hood.
     */
    void createNotificationForRole(String roleCode, String title, String message, String type, String actionUrl);

    /**
     * Broadcasts a notification to active internal admin accounts holding the role.
     */
    void createNotificationForAdminRole(String roleCode, String title, String message, String type, String actionUrl);

    void createNotification(UUID recipientUserId, String recipientEmail,
                            String title, String message, String type);

    void createNotification(UUID recipientUserId, String recipientEmail,
                            String title, String message, String type, String actionUrl);

    void createAdminNotification(UUID recipientAdminId, String recipientEmail,
                                 String title, String message, String type, String actionUrl);

    void createAdminNotificationOnce(
            String dedupeKey,
            UUID recipientAdminId,
            String recipientEmail,
            String title,
            String message,
            String type,
            String actionUrl
    );

    /**
     * Creates and sends a notification at most once for the supplied business
     * event key. Callers use this from an AFTER_COMMIT callback.
     */
    void createNotificationOnce(
            String dedupeKey,
            UUID recipientUserId,
            String recipientEmail,
            String title,
            String message,
            String type
    );

    void createNotificationOnce(
            String dedupeKey,
            UUID recipientUserId,
            String recipientEmail,
            String title,
            String message,
            String type,
            String actionUrl
    );

    void sendTestEmailOnly(String recipientEmail, String title, String message, String type);
}
