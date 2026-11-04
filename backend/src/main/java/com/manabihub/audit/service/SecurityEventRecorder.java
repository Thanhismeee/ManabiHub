package com.manabihub.audit.service;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * NFR-SEC-51: ghi lại sự kiện an ninh khi một thao tác bị từ chối vì vi phạm
 * quyền sở hữu (BR-OWN-01).
 * <p>
 * Chạy trong giao dịch riêng ({@link Propagation#REQUIRES_NEW}) vì lời gọi luôn
 * đi kèm một ngoại lệ ném ngay sau đó, khiến giao dịch gọi bị rollback. Nếu ghi
 * trong cùng giao dịch, bản ghi sẽ bị cuốn theo và không để lại dấu vết nào.
 * <p>
 * Lỗi khi ghi được nuốt và chỉ log lại: không ghi được nhật ký an ninh không
 * được phép biến một phản hồi 403 thành 500.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventRecorder {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAccessDenied(
            UUID actorUserId,
            String action,
            String targetType,
            UUID targetId,
            Map<String, Object> metadata
    ) {
        try {
            auditLogRepository.saveAndFlush(AuditLog.builder()
                    .actorType("USER")
                    .actorUserId(actorUserId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .metadata(metadata)
                    .build());
        } catch (RuntimeException ex) {
            log.error("Khong ghi duoc su kien an ninh {} cho user {}", action, actorUserId, ex);
        }
    }

    /**
     * NFR-SEC-28: từ chối truy cập với admin nội bộ. Phải ghi vào actor_admin_id
     * chứ không phải actor_user_id: id trong token admin thuộc bảng
     * internal_admin_accounts, ghi nhầm cột sẽ vi phạm khoá ngoại và bản ghi
     * biến mất lặng lẽ trong khối catch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAdminAccessDenied(
            UUID actorAdminId,
            String actorRoleCode,
            String action,
            String targetType,
            Map<String, Object> metadata
    ) {
        try {
            auditLogRepository.saveAndFlush(AuditLog.builder()
                    .actorType("INTERNAL_ADMIN")
                    .actorAdminId(actorAdminId)
                    .actorRoleCode(actorRoleCode)
                    .action(action)
                    .targetType(targetType)
                    .metadata(metadata)
                    .build());
        } catch (RuntimeException ex) {
            log.error("Khong ghi duoc su kien an ninh {} cho admin {}", action, actorAdminId, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnattributedAccessDenied(
            String action,
            String targetType,
            Map<String, Object> metadata
    ) {
        try {
            auditLogRepository.saveAndFlush(AuditLog.builder()
                    .actorType("SYSTEM")
                    .action(action)
                    .targetType(targetType)
                    .metadata(metadata)
                    .build());
        } catch (RuntimeException ex) {
            log.error("Khong ghi duoc su kien an ninh {} khong co actor", action, ex);
        }
    }
}
