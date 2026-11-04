package com.manabihub.audit.service;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class SecurityAuditService {

    private final AuditLogRepository auditLogRepository;

    public SecurityAuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Logs security event when duplicate identity attempt is detected.
     * Uses REQUIRES_NEW propagation to ensure audit log is saved even if outer transaction rolls back.
     * CRITICAL: Absolutely no raw CCCD or identity fingerprint in the audit log payload.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDuplicateIdentityAudit(UUID teacherId, UUID actorUserId, String ipAddress, String userAgent) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("USER")
                .actorUserId(actorUserId)
                .actorRoleCode("TEACHER")
                .action("KYC_DUPLICATE_IDENTITY_DETECTED")
                .targetType("TEACHER_PROFILE")
                .targetId(teacherId)
                .afterValue(Map.of(
                        "reason", "Duplicate CCCD identity claim detected across different teachers",
                        "status", "BLOCKED"
                ))
                .metadata(Map.of(
                        "uc", "UC-22",
                        "module", "IDENTITY_VERIFICATION",
                        "msg", MessageCodes.MSG_KYC_008
                ))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * Persists a student duplicate-CCCD attempt independently of the failed
     * verification transaction. Raw CCCD values and fingerprints are never logged.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logStudentDuplicateIdentityAudit(UUID studentId, UUID actorUserId) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("USER")
                .actorUserId(actorUserId)
                .actorRoleCode("STUDENT")
                .action("BR-KYC-CCCD-DUPLICATE")
                .targetType("STUDENT_PROFILE")
                .targetId(studentId)
                .afterValue(Map.of(
                        "reason", "Duplicate CCCD identity claim detected across different accounts",
                        "status", "BLOCKED"
                ))
                .metadata(Map.of(
                        "module", "IDENTITY_VERIFICATION",
                        "msg", MessageCodes.MSG_IDV_002
                ))
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * Logs security event when an invalid or mismatched transaction binding is detected.
     * Uses REQUIRES_NEW propagation to ensure audit log is saved even if outer transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logMismatchBindingAudit(UUID teacherId, UUID actorUserId, String ipAddress, String userAgent) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("USER")
                .actorUserId(actorUserId)
                .actorRoleCode("TEACHER")
                .action("KYC_MISMATCH_TRANSACTION_BINDING_DETECTED")
                .targetType("TEACHER_PROFILE")
                .targetId(teacherId)
                .afterValue(Map.of(
                        "reason", "Transaction or session mismatch detected. Replay or cross-user attempt.",
                        "status", "BLOCKED"
                ))
                .metadata(Map.of(
                        "uc", "UC-22",
                        "module", "IDENTITY_VERIFICATION",
                        "msg", MessageCodes.MSG_KYC_008
                ))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * Persists the duplicate JLPT claim attempt even when the surrounding KYC
     * submission is rolled back. Certificate codes are deliberately omitted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDuplicateCertificateAudit(
            UUID teacherId,
            UUID actorUserId,
            String ipAddress,
            String userAgent
    ) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("USER")
                .actorUserId(actorUserId)
                .actorRoleCode("TEACHER_CANDIDATE")
                .action("KYC_DUPLICATE_JLPT_CERTIFICATE_DETECTED")
                .targetType("TEACHER_PROFILE")
                .targetId(teacherId)
                .afterValue(Map.of(
                        "reason", "Duplicate JLPT certificate claim detected across different teachers",
                        "status", "BLOCKED"
                ))
                .metadata(Map.of(
                        "uc", "UC-28",
                        "module", "CERTIFICATE_VERIFICATION",
                        "msg", MessageCodes.KYC_CERTIFICATE_ALREADY_CLAIMED
                ))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * Logs security audit when historical duplicate identity claims are quarantined during backfill.
     * REQUIRES_NEW transaction ensures log persistence. No PII is logged.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logBackfillQuarantineAudit(UUID teacherId, int conflictingTeacherCount) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("SYSTEM")
                .actorRoleCode("SYSTEM")
                .action("KYC_BACKFILL_DUPLICATE_QUARANTINED")
                .targetType("TEACHER_PROFILE")
                .targetId(teacherId)
                .afterValue(Map.of(
                        "reason", "Historical duplicate identity claim quarantined during startup backfill",
                        "conflictingTeachers", conflictingTeacherCount,
                        "resolutionRequired", "MANUAL_ADMIN_RESOLUTION_REQUIRED"
                ))
                .metadata(Map.of(
                        "uc", "UC-22",
                        "module", "HISTORICAL_BACKFILL"
                ))
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInternalAdminAuthenticationFailure(
            UUID adminId,
            String role,
            String reason,
            String ipAddress,
            String userAgent
    ) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(adminId)
                .actorRoleCode(role)
                .action("LOGIN_FAILED")
                .targetType("ADMIN_AUTHENTICATION")
                .metadata(Map.of("reason", reason))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        auditLogRepository.save(auditLog);
    }

    /**
     * Logs a generic VNPT server verification event with REQUIRES_NEW propagation.
     * <p>
     * Persists even if the outer transaction rolls back.
     * CRITICAL: No PII, no raw transaction IDs, no CCCD in the payload.
     *
     * @param action       one of: SERVER_VERIFIED, PROVIDER_REJECTED, PROVIDER_TIMEOUT,
     *                     EXPIRED, MAX_ATTEMPTS, TRANSACTION_MISMATCH, SESSION_MISMATCH,
     *                     STALE_TIMESTAMP, FUTURE_TIMESTAMP, MISSING_SERVER_IDENTITY,
     *                     PROVIDER_NOT_CONFIGURED, IDENTITY_CLAIM_FAILED, DUPLICATE_TRANSACTION
     * @param teacherId    the teacher profile ID (not user ID)
     * @param requestId    the KycRequest ID (nullable for pre-bind events)
     * @param actorUserId  the acting user ID
     * @param ipAddress    client IP
     * @param userAgent    client User-Agent
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logVerificationEvent(
            String action,
            UUID teacherId,
            UUID requestId,
            UUID actorUserId,
            String ipAddress,
            String userAgent
    ) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("USER")
                .actorUserId(actorUserId)
                .actorRoleCode("TEACHER")
                .action("KYC_" + action)
                .targetType(requestId != null ? "KYC_REQUEST" : "TEACHER_PROFILE")
                .targetId(requestId != null ? requestId : teacherId)
                .afterValue(Map.of(
                        "event", action,
                        "status", "RECORDED"
                ))
                .metadata(Map.of(
                        "uc", "UC-22",
                        "module", "SERVER_VERIFICATION"
                ))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        auditLogRepository.save(auditLog);
    }
}
