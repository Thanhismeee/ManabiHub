package com.manabihub.notification.repository;

import com.manabihub.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByDedupeKey(String dedupeKey);

    @Query(value = """
            SELECT admin.id
            FROM internal_admin_accounts admin
            JOIN internal_admin_roles admin_role ON admin_role.admin_account_id = admin.id
            JOIN roles role ON role.id = admin_role.role_id
            WHERE role.code = :roleCode
              AND admin.account_status = 'ACTIVE'
            """, nativeQuery = true)
    List<UUID> findActiveAdminIdsByRoleCode(@Param("roleCode") String roleCode);

    @Query(value = """
            SELECT DISTINCT admin.email
            FROM internal_admin_accounts admin
            JOIN internal_admin_roles admin_role ON admin_role.admin_account_id = admin.id
            JOIN roles role ON role.id = admin_role.role_id
            WHERE role.code = :roleCode
              AND admin.account_status = 'ACTIVE'
              AND admin.email IS NOT NULL
              AND BTRIM(admin.email) <> ''
            """, nativeQuery = true)
    List<String> findActiveAdminEmailsByRoleCode(@Param("roleCode") String roleCode);

    @Query("SELECT n FROM Notification n WHERE (n.recipientUserId = :userId OR n.recipientAdminId = :userId) ORDER BY n.createdAt DESC")
    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE (n.recipientUserId = :userId OR n.recipientAdminId = :userId) AND n.isRead = :isRead ORDER BY n.createdAt DESC")
    Page<Notification> findAllByUserIdAndIsReadOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("isRead") boolean isRead, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE (n.recipientUserId = :userId OR n.recipientAdminId = :userId) AND n.notificationType = :notificationType ORDER BY n.createdAt DESC")
    Page<Notification> findAllByUserIdAndNotificationTypeOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("notificationType") String notificationType, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE (n.recipientUserId = :userId OR n.recipientAdminId = :userId) AND n.notificationType = :notificationType AND n.isRead = :isRead ORDER BY n.createdAt DESC")
    Page<Notification> findAllByUserIdAndTypeAndIsReadOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("notificationType") String notificationType, @Param("isRead") boolean isRead, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.recipientUserId = :userId OR n.recipientAdminId = :userId) AND n.isRead = false")
    long countUnreadByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now " +
            "WHERE (n.recipientUserId = :userId OR n.recipientAdminId = :userId) AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
