package com.manabihub.identity.service;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.identity.entity.InternalAdminInvitation;
import com.manabihub.identity.entity.InternalAdminPasswordReset;
import com.manabihub.identity.repository.InternalAdminInvitationRepository;
import com.manabihub.identity.repository.InternalAdminPasswordResetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalAdminCredentialDeliveryFailureService {

    private final InternalAdminInvitationRepository invitationRepository;
    private final InternalAdminPasswordResetRepository passwordResetRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecureTokenService tokenService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeInvitation(String rawToken) {
        String tokenHash = tokenService.hash(rawToken);
        invitationRepository.findByTokenHashForUpdate(tokenHash)
                .filter(this::isOpen)
                .ifPresent(invitation -> {
                    invitation.setRevokedAt(Instant.now());
                    invitationRepository.save(invitation);
                    recordFailure(
                            invitation.getAdminAccountId(),
                            "ADMIN_INVITATION_EMAIL_FAILED"
                    );
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokePasswordReset(String rawToken) {
        String tokenHash = tokenService.hash(rawToken);
        passwordResetRepository.findByTokenHashForUpdate(tokenHash)
                .filter(this::isOpen)
                .ifPresent(passwordReset -> {
                    passwordReset.setRevokedAt(Instant.now());
                    passwordResetRepository.save(passwordReset);
                    recordFailure(
                            passwordReset.getAdminAccountId(),
                            "ADMIN_PASSWORD_RESET_EMAIL_FAILED"
                    );
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPasswordChangedNotificationFailure(UUID adminAccountId) {
        recordFailure(
                adminAccountId,
                "ADMIN_PASSWORD_CHANGED_EMAIL_FAILED"
        );
    }

    private boolean isOpen(InternalAdminInvitation invitation) {
        return invitation.getUsedAt() == null && invitation.getRevokedAt() == null;
    }

    private boolean isOpen(InternalAdminPasswordReset passwordReset) {
        return passwordReset.getUsedAt() == null
                && passwordReset.getRevokedAt() == null;
    }

    private void recordFailure(UUID adminAccountId, String action) {
        auditLogRepository.save(AuditLog.builder()
                .actorType("SYSTEM")
                .action(action)
                .targetType("INTERNAL_ADMIN_ACCOUNT")
                .targetId(adminAccountId)
                .metadata(Map.of(
                        "delivery", "EMAIL",
                        "credentialRevoked", action.contains("RESET")
                                || action.contains("INVITATION")
                ))
                .build());
    }
}
