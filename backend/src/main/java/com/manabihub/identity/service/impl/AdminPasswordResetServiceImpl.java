package com.manabihub.identity.service.impl;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.InternalAdminPasswordReset;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.event.InternalAdminPasswordChangedEvent;
import com.manabihub.identity.event.InternalAdminPasswordResetIssuedEvent;
import com.manabihub.identity.event.InternalAdminSessionsInvalidatedEvent;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.InternalAdminPasswordResetRepository;
import com.manabihub.identity.service.AdminLoginProtection;
import com.manabihub.identity.service.AdminPasswordChangeProtection;
import com.manabihub.identity.service.AdminPasswordResetRequestDispatcher;
import com.manabihub.identity.service.AdminPasswordResetRequestLimiter;
import com.manabihub.identity.service.AdminPasswordResetService;
import com.manabihub.identity.service.InternalAdminPasswordPolicy;
import com.manabihub.identity.service.SecureTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPasswordResetServiceImpl implements AdminPasswordResetService {

    private final InternalAdminAccountRepository accountRepository;
    private final InternalAdminPasswordResetRepository passwordResetRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final InternalAdminPasswordPolicy passwordPolicy;
    private final SecureTokenService tokenService;
    private final AdminPasswordResetRequestLimiter requestLimiter;
    private final AdminPasswordResetRequestDispatcher requestDispatcher;
    private final AdminLoginProtection loginProtection;
    private final AdminPasswordChangeProtection changeProtection;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${manabihub.admin-auth.password-reset-minutes:30}")
    private long resetTtlMinutes;

    @Value("${manabihub.admin-auth.password-reset-cooldown-seconds:60}")
    private long resetCooldownSeconds;

    @Override
    public void request(String email, String ipAddress, String userAgent) {
        String normalizedEmail = email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
        try {
            if (!requestLimiter.allow(normalizedEmail, ipAddress)) {
                return;
            }
        } catch (RuntimeException limiterFailure) {
            log.warn(
                    "Admin password reset admission check failed ({})",
                    limiterFailure.getClass().getSimpleName()
            );
            return;
        }
        requestDispatcher.dispatch(
                () -> issueReset(normalizedEmail, ipAddress, userAgent)
        );
    }

    private void issueReset(String normalizedEmail, String ipAddress, String userAgent) {
        InternalAdminAccount account = accountRepository
                .findByEmailIgnoreCaseForUpdate(normalizedEmail)
                .filter(candidate -> candidate.getAccountStatus() == AccountStatus.ACTIVE)
                .orElse(null);
        if (account == null) {
            return;
        }

        Instant now = Instant.now();
        InternalAdminPasswordReset openReset = passwordResetRepository
                .findOpenForAccount(account.getId())
                .orElse(null);
        if (openReset != null
                && openReset.getCreatedAt() != null
                && Duration.between(openReset.getCreatedAt(), now).toSeconds()
                        < resetCooldownSeconds) {
            return;
        }

        passwordResetRepository.revokeOpenForAccount(account.getId(), now);
        String rawToken = tokenService.randomToken();
        InternalAdminPasswordReset passwordReset = new InternalAdminPasswordReset();
        passwordReset.setAdminAccountId(account.getId());
        passwordReset.setTokenHash(tokenService.hash(rawToken));
        passwordReset.setExpiresAt(now.plus(resetTtlMinutes, ChronoUnit.MINUTES));
        passwordResetRepository.saveAndFlush(passwordReset);

        auditLogRepository.save(AuditLog.builder()
                .actorType("SYSTEM")
                .action("ADMIN_PASSWORD_RESET_REQUESTED")
                .targetType("INTERNAL_ADMIN_ACCOUNT")
                .targetId(account.getId())
                .metadata(Map.of("delivery", "EMAIL"))
                .ipAddress(safeMetadata(ipAddress))
                .userAgent(safeMetadata(userAgent))
                .build());
        eventPublisher.publishEvent(new InternalAdminPasswordResetIssuedEvent(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                rawToken,
                passwordReset.getExpiresAt()
        ));
    }

    @Override
    @Transactional
    public void reset(
            String rawToken,
            String newPassword,
            String ipAddress,
            String userAgent
    ) {
        passwordPolicy.validate(newPassword);
        String tokenHash = safeHash(rawToken);
        InternalAdminPasswordReset candidate = passwordResetRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(this::invalidReset);
        Instant now = Instant.now();
        if (!isOpen(candidate, now)) {
            throw invalidReset();
        }

        InternalAdminAccount account = accountRepository
                .findByIdForRoleUpdate(candidate.getAdminAccountId())
                .orElseThrow(this::invalidReset);
        InternalAdminPasswordReset passwordReset = passwordResetRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidReset);
        if (!passwordReset.getAdminAccountId().equals(account.getId())
                || !isOpen(passwordReset, Instant.now())) {
            throw invalidReset();
        }
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw invalidReset();
        }
        rejectPasswordReuse(account, newPassword);

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setCredentialVersion(account.getCredentialVersion() + 1);
        accountRepository.save(account);
        passwordReset.setUsedAt(now);
        passwordResetRepository.save(passwordReset);
        passwordResetRepository.revokeOpenForAccount(account.getId(), now);
        loginProtection.reset(
                account.getEmail().toLowerCase(Locale.ROOT),
                ipAddress
        );

        recordPasswordChange(
                account,
                "ADMIN_PASSWORD_RESET_COMPLETED",
                ipAddress,
                userAgent
        );
        eventPublisher.publishEvent(new InternalAdminPasswordChangedEvent(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                now
        ));
        publishSessionInvalidation(account.getId(), "PASSWORD_RESET", now);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public void change(
            UUID adminId,
            String currentPassword,
            String newPassword,
            String ipAddress,
            String userAgent
    ) {
        changeProtection.check(adminId, ipAddress);
        passwordPolicy.validate(newPassword);
        InternalAdminAccount account = accountRepository
                .findByIdForRoleUpdate(adminId)
                .orElseThrow(this::invalidCurrentPassword);
        if (account.getAccountStatus() != AccountStatus.ACTIVE
                || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            changeProtection.recordFailure(adminId, ipAddress);
            recordPasswordChangeFailure(account, ipAddress, userAgent);
            throw invalidCurrentPassword();
        }
        rejectPasswordReuse(account, newPassword);

        Instant now = Instant.now();
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setCredentialVersion(account.getCredentialVersion() + 1);
        accountRepository.save(account);
        passwordResetRepository.revokeOpenForAccount(account.getId(), now);
        loginProtection.reset(
                account.getEmail().toLowerCase(Locale.ROOT),
                ipAddress
        );
        changeProtection.reset(account.getId());
        recordPasswordChange(
                account,
                "ADMIN_PASSWORD_CHANGED",
                ipAddress,
                userAgent
        );
        eventPublisher.publishEvent(new InternalAdminPasswordChangedEvent(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                now
        ));
        publishSessionInvalidation(account.getId(), "PASSWORD_CHANGED", now);
    }

    private void rejectPasswordReuse(
            InternalAdminAccount account,
            String newPassword
    ) {
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new BusinessException(
                    MessageCodes.ADMIN_PASSWORD_REUSE_FORBIDDEN,
                    "New password must differ from the current password"
            );
        }
    }

    private void recordPasswordChange(
            InternalAdminAccount account,
            String action,
            String ipAddress,
            String userAgent
    ) {
        auditLogRepository.save(AuditLog.builder()
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(account.getId())
                .actorRoleCode(account.getRole().getCode().name())
                .action(action)
                .targetType("INTERNAL_ADMIN_ACCOUNT")
                .targetId(account.getId())
                .metadata(Map.of(
                        "sessionsInvalidated", true,
                        "cleanupMode", "AFTER_COMMIT"
                ))
                .ipAddress(safeMetadata(ipAddress))
                .userAgent(safeMetadata(userAgent))
                .build());
    }

    private void recordPasswordChangeFailure(
            InternalAdminAccount account,
            String ipAddress,
            String userAgent
    ) {
        auditLogRepository.save(AuditLog.builder()
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(account.getId())
                .actorRoleCode(account.getRole() == null
                        ? "UNKNOWN"
                        : account.getRole().getCode().name())
                .action("ADMIN_PASSWORD_CHANGE_FAILED")
                .targetType("INTERNAL_ADMIN_ACCOUNT")
                .targetId(account.getId())
                .metadata(Map.of("reason", "INVALID_CURRENT_PASSWORD"))
                .ipAddress(safeMetadata(ipAddress))
                .userAgent(safeMetadata(userAgent))
                .build());
    }

    private boolean isOpen(
            InternalAdminPasswordReset passwordReset,
            Instant now
    ) {
        return passwordReset.getUsedAt() == null
                && passwordReset.getRevokedAt() == null
                && now.isBefore(passwordReset.getExpiresAt());
    }

    private void publishSessionInvalidation(
            UUID adminAccountId,
            String reason,
            Instant occurredAt
    ) {
        eventPublisher.publishEvent(new InternalAdminSessionsInvalidatedEvent(
                adminAccountId,
                reason,
                occurredAt
        ));
    }

    private String safeHash(String rawToken) {
        try {
            return tokenService.hash(rawToken);
        } catch (IllegalArgumentException exception) {
            throw invalidReset();
        }
    }

    private BusinessException invalidReset() {
        return new BusinessException(
                MessageCodes.ADMIN_PASSWORD_RESET_INVALID,
                "The password reset link is invalid or expired",
                HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException invalidCurrentPassword() {
        return new BusinessException(
                MessageCodes.ADMIN_CURRENT_PASSWORD_INVALID,
                "Current password is invalid",
                HttpStatus.BAD_REQUEST
        );
    }

    private String safeMetadata(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), 500));
    }
}
