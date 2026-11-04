package com.manabihub.notification.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.mail.EmailService;
import com.manabihub.notification.dto.NotificationResponse;
import com.manabihub.notification.entity.Notification;
import com.manabihub.notification.repository.NotificationRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.notification.NotificationTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Page<NotificationResponse> listMyNotifications(UUID userId, String type,
                                                          Boolean isRead, Pageable pageable) {
        Page<Notification> page;

        boolean hasType = type != null && !type.isBlank();
        boolean hasReadFilter = isRead != null;

        if (hasType && hasReadFilter) {
            page = notificationRepository
                    .findAllByUserIdAndTypeAndIsReadOrderByCreatedAtDesc(
                            userId, type, isRead, pageable);
        } else if (hasType) {
            page = notificationRepository
                    .findAllByUserIdAndNotificationTypeOrderByCreatedAtDesc(
                            userId, type, pageable);
        } else if (hasReadFilter) {
            page = notificationRepository
                    .findAllByUserIdAndIsReadOrderByCreatedAtDesc(
                            userId, isRead, pageable);
        } else {
            page = notificationRepository
                    .findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return page.map(this::toResponse);
    }

    @Override
    public long countUnread(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.NOTIFICATION_NOT_FOUND,
                        "Notification not found",
                        HttpStatus.NOT_FOUND
                ));

        if (!userId.equals(notification.getRecipientUserId()) && !userId.equals(notification.getRecipientAdminId())) {
            throw new BusinessException(
                    MessageCodes.AUTH_FORBIDDEN,
                    "You do not have permission to access this notification",
                    HttpStatus.FORBIDDEN
            );
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    @Override
    @Transactional
    public NotificationResponse markAsUnread(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.NOTIFICATION_NOT_FOUND,
                        "Notification not found",
                        HttpStatus.NOT_FOUND
                ));

        if (!userId.equals(notification.getRecipientUserId()) && !userId.equals(notification.getRecipientAdminId())) {
            throw new BusinessException(
                    MessageCodes.AUTH_FORBIDDEN,
                    "You do not have permission to access this notification",
                    HttpStatus.FORBIDDEN
            );
        }

        if (notification.isRead()) {
            notification.setRead(false);
            notification.setReadAt(null);
            notificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsReadByUserId(userId, Instant.now());
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void createNotificationForRole(String roleCode, String title, String message, String type, String actionUrl) {
        String sql = "SELECT ur.user_id FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE r.code = :roleCode";

        @SuppressWarnings("unchecked")
        List<UUID> userIds = entityManager.createNativeQuery(sql, UUID.class)
                .setParameter("roleCode", roleCode)
                .getResultList();

        List<Notification> notifications = userIds.stream().map(userId -> {
            return Notification.builder()
                    .recipientUserId(userId)
                    .title(title)
                    .message(message)
                    .notificationType(type)
                    .actionUrl(actionUrl)
                    .isRead(false)
                    .createdAt(Instant.now())
                    .build();
        }).toList();

        notificationRepository.saveAll(notifications);
        log.info("Broadcasted notification to {} users with role {}", userIds.size(), roleCode);
    }

    @Override
    @Transactional
    public void createNotificationForAdminRole(
            String roleCode,
            String title,
            String message,
            String type,
            String actionUrl
    ) {
        List<UUID> adminIds = notificationRepository.findActiveAdminIdsByRoleCode(roleCode);
        List<Notification> notifications = adminIds.stream()
                .map(adminId -> Notification.builder()
                        .recipientAdminId(adminId)
                        .title(title)
                        .message(message)
                        .notificationType(type)
                        .actionUrl(actionUrl)
                        .isRead(false)
                        .createdAt(Instant.now())
                        .build())
                .toList();

        notificationRepository.saveAll(notifications);
        notificationRepository.findActiveAdminEmailsByRoleCode(roleCode)
                .forEach(email -> scheduleEmailAfterCommit(email, title, message, type));
        log.info("Broadcasted notification to {} internal admins with role {}", adminIds.size(), roleCode);
    }

    @Override
    @Transactional
    public void createNotification(UUID recipientUserId, String recipientEmail,
                                   String title, String message, String type) {
        createNotification(recipientUserId, recipientEmail, title, message, type, null);
    }

    @Override
    @Transactional
    public void createNotification(UUID recipientUserId, String recipientEmail,
                                   String title, String message, String type, String actionUrl) {
        Notification notification = Notification.builder()
                .recipientUserId(recipientUserId)
                .title(title)
                .message(message)
                .notificationType(type)
                .actionUrl(actionUrl)
                .build();

        notificationRepository.saveAndFlush(notification);
        log.info("Notification created for user {} — type={}, title={}",
                recipientUserId, type, title);

        if (recipientEmail != null && !recipientEmail.isBlank()) {
            scheduleEmailAfterCommit(recipientEmail, title, message, type);
        }
    }

    @Override
    @Transactional
    public void createAdminNotification(UUID recipientAdminId, String recipientEmail,
                                        String title, String message, String type, String actionUrl) {
        notificationRepository.saveAndFlush(Notification.builder()
                .recipientAdminId(recipientAdminId)
                .title(title)
                .message(message)
                .notificationType(type)
                .actionUrl(actionUrl)
                .build());
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            scheduleEmailAfterCommit(recipientEmail, title, message, type);
        }
    }

    @Override
    @Transactional
    public void createAdminNotificationOnce(
            String dedupeKey,
            UUID recipientAdminId,
            String recipientEmail,
            String title,
            String message,
            String type,
            String actionUrl
    ) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO notifications (
                    id,
                    recipient_admin_id,
                    title,
                    message,
                    notification_type,
                    action_url,
                    dedupe_key,
                    is_read,
                    created_at
                )
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, FALSE, NOW())
                ON CONFLICT DO NOTHING
                """,
                recipientAdminId,
                title,
                message,
                type,
                actionUrl,
                dedupeKey);
        if (inserted == 0) {
            log.info("Skipped duplicate admin notification {}", dedupeKey);
            return;
        }
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            scheduleEmailAfterCommit(recipientEmail, title, message, type);
        }
    }

    @Override
    @Transactional
    public void createNotificationOnce(
            String dedupeKey,
            UUID recipientUserId,
            String recipientEmail,
            String title,
            String message,
            String type
    ) {
        createNotificationOnce(dedupeKey, recipientUserId, recipientEmail, title, message, type, null);
    }

    @Override
    @Transactional
    public void createNotificationOnce(
            String dedupeKey,
            UUID recipientUserId,
            String recipientEmail,
            String title,
            String message,
            String type,
            String actionUrl
    ) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO notifications (
                    id,
                    recipient_user_id,
                    title,
                    message,
                    notification_type,
                    action_url,
                    dedupe_key,
                    is_read,
                    created_at
                )
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, FALSE, NOW())
                ON CONFLICT DO NOTHING
                """,
                recipientUserId,
                title,
                message,
                type,
                actionUrl,
                dedupeKey);
        if (inserted == 0) {
            log.info("Skipped duplicate notification {}", dedupeKey);
            return;
        }
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            scheduleEmailAfterCommit(recipientEmail, title, message, type);
        }
    }

    @Override
    public void sendTestEmailOnly(String recipientEmail, String title, String message, String type) {
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            String emailBody = buildEmailBody(title, message, type);
            emailService.sendEmail(recipientEmail, "[ManabiHub] " + title, emailBody);
        }
    }

    private NotificationResponse toResponse(Notification entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .notificationType(entity.getNotificationType())
                .actionUrl(entity.getActionUrl())
                .read(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .readAt(entity.getReadAt())
                .build();
    }

    private String buildEmailBody(String title, String message, String type) {
        return """
                <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 20px; border-radius: 10px 10px 0 0;">
                        <h1 style="color: white; margin: 0; font-size: 24px;">🔔 ManabiHub</h1>
                    </div>
                    <div style="background: #ffffff; padding: 30px; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 10px 10px;">
                        <span style="display: inline-block; background: #e8f0fe; color: #1967d2; padding: 4px 12px; border-radius: 12px; font-size: 12px; margin-bottom: 16px;">%s</span>
                        <h2 style="color: #333; margin: 0 0 12px 0;">%s</h2>
                        <p style="color: #555; line-height: 1.6;">%s</p>
                        <hr style="border: none; border-top: 1px solid #eee; margin: 24px 0;">
                        <p style="color: #999; font-size: 12px;">Đây là email tự động từ hệ thống ManabiHub. Vui lòng không trả lời email này.</p>
                    </div>
                </div>
                """.formatted(
                        HtmlUtils.htmlEscape(NotificationTypes.vietnameseLabel(type)),
                        HtmlUtils.htmlEscape(title),
                        HtmlUtils.htmlEscape(message));
    }

    private void scheduleEmailAfterCommit(String recipientEmail, String title, String message, String type) {
        Runnable delivery = () -> emailService.sendEmail(
                recipientEmail,
                "[ManabiHub] " + title,
                buildEmailBody(title, message, type)
        );
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delivery.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delivery.run();
            }
        });
    }
}
