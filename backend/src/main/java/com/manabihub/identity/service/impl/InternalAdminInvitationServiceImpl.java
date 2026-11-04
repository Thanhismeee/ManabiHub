package com.manabihub.identity.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.InternalAdminInvitation;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.InternalAdminInvitationStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.event.InternalAdminInvitationIssuedEvent;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.InternalAdminInvitationRepository;
import com.manabihub.identity.repository.RoleRepository;
import com.manabihub.identity.service.InternalAdminInvitationService;
import com.manabihub.identity.service.InternalAdminPasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalAdminInvitationServiceImpl implements InternalAdminInvitationService {

    private static final List<RoleCode> INTERNAL_ROLES = List.of(
            RoleCode.SYSTEM_ADMIN,
            RoleCode.COURSE_MANAGER,
            RoleCode.FINANCE_MANAGER
    );
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final InternalAdminAccountRepository adminRepository;
    private final InternalAdminInvitationRepository invitationRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final InternalAdminPasswordPolicy passwordPolicy;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${manabihub.admin-invitation.ttl-hours:24}")
    private long invitationTtlHours;

    @Override
    @Transactional
    public InternalAdminAccount invite(
            UUID actorId,
            String email,
            String fullName,
            RoleCode roleCode,
            String reason
    ) {
        InternalAdminAccount actor = requireLiveSystemAdmin(actorId);
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = normalizeName(fullName);
        requireInternalRole(roleCode);

        InternalAdminAccount account = adminRepository.findByEmailIgnoreCaseForUpdate(normalizedEmail)
                .orElseGet(InternalAdminAccount::new);
        if (account.getId() != null && account.getAccountStatus() != AccountStatus.DISABLED) {
            throw invitationConflict();
        }

        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.INTERNAL_ROLE_INVALID,
                        "Internal role is not configured"
                ));

        RoleCode previousRole = account.getRole() == null ? null : account.getRole().getCode();
        account.setEmail(normalizedEmail);
        account.setFullName(normalizedName);
        account.setRole(role);
        account.setAccountStatus(AccountStatus.DISABLED);
        account.setPasswordHash(passwordEncoder.encode(randomToken()));
        InternalAdminAccount saved = saveInvitedAccount(account);

        issueInvitation(actor, saved);
        auditLogService.logAdminAction(
                actorId,
                actor.getRole().getCode().name(),
                "CREATE_INTERNAL_ADMIN_INVITATION",
                "INTERNAL_ADMIN_ACCOUNT",
                saved.getId(),
                previousRole == null ? Map.of() : Map.of("role", previousRole.name()),
                Map.of(
                        "email", saved.getEmail(),
                        "role", roleCode.name(),
                        "status", AccountStatus.DISABLED.name()
                ),
                Map.of("reason", reason.trim())
        );
        return saved;
    }

    @Override
    @Transactional
    public InternalAdminAccount resend(UUID actorId, UUID accountId, String reason) {
        InternalAdminAccount actor = requireLiveSystemAdmin(actorId);
        InternalAdminAccount account = adminRepository.findByIdForRoleUpdate(accountId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Internal administrator account not found",
                        HttpStatus.NOT_FOUND
                ));
        if (account.getAccountStatus() != AccountStatus.DISABLED
                || account.getRole() == null
                || !INTERNAL_ROLES.contains(account.getRole().getCode())
                || account.getEmail().endsWith("@manabihub.local")) {
            throw invitationConflict();
        }
        InvitationSummary currentInvitation = latestInvitationSummaries(List.of(accountId))
                .getOrDefault(accountId, InvitationSummary.none());
        if (currentInvitation.status() == InternalAdminInvitationStatus.ACCEPTED
                || currentInvitation.status() == InternalAdminInvitationStatus.NONE) {
            throw invitationConflict();
        }

        issueInvitation(actor, account);
        auditLogService.logAdminAction(
                actorId,
                actor.getRole().getCode().name(),
                "RESEND_INTERNAL_ADMIN_INVITATION",
                "INTERNAL_ADMIN_ACCOUNT",
                account.getId(),
                Map.of(),
                Map.of("invitationQueued", true),
                Map.of(
                        "reason", reason.trim(),
                        "targetEmail", account.getEmail()
                )
        );
        return account;
    }

    @Override
    @Transactional
    public void revokeOpenInvitations(UUID accountId, Instant revokedAt) {
        invitationRepository.revokeOpenInvitations(accountId, revokedAt);
    }

    @Override
    @Transactional
    public void accept(
            String rawToken,
            String password,
            String ipAddress,
            String userAgent
    ) {
        passwordPolicy.validate(password);
        String tokenHash = hashToken(rawToken);
        InternalAdminInvitation candidate = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidInvitation);
        InternalAdminAccount account = adminRepository
                .findByIdForRoleUpdate(candidate.getAdminAccountId())
                .orElseThrow(this::invalidInvitation);
        InternalAdminInvitation invitation = invitationRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidInvitation);
        Instant now = Instant.now();
        if (invitation.getUsedAt() != null
                || invitation.getRevokedAt() != null
                || !now.isBefore(invitation.getExpiresAt())
                || !invitation.getAdminAccountId().equals(account.getId())) {
            throw invalidInvitation();
        }

        if (account.getAccountStatus() != AccountStatus.DISABLED
                || account.getRole() == null
                || !INTERNAL_ROLES.contains(account.getRole().getCode())) {
            throw invalidInvitation();
        }

        account.setPasswordHash(passwordEncoder.encode(password));
        account.setCredentialVersion(account.getCredentialVersion() + 1);
        account.setAccountStatus(AccountStatus.ACTIVE);
        adminRepository.save(account);
        invitation.setUsedAt(now);
        invitationRepository.save(invitation);

        auditLogService.logAdminAction(
                account.getId(),
                account.getRole().getCode().name(),
                "ACTIVATE_INTERNAL_ADMIN_ACCOUNT",
                "INTERNAL_ADMIN_ACCOUNT",
                account.getId(),
                Map.of("status", AccountStatus.DISABLED.name()),
                Map.of("status", AccountStatus.ACTIVE.name()),
                Map.of(
                        "source", "ONE_TIME_INVITATION",
                        "ipAddress", safeMetadata(ipAddress),
                        "userAgent", safeMetadata(userAgent)
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, InvitationSummary> latestInvitationSummaries(
            Collection<UUID> accountIds
    ) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        Instant now = Instant.now();
        Map<UUID, InvitationSummary> summaries = new LinkedHashMap<>();
        invitationRepository
                .findAllByAdminAccountIdInOrderByCreatedAtDesc(accountIds)
                .forEach(invitation -> summaries.putIfAbsent(
                        invitation.getAdminAccountId(),
                        toSummary(invitation, now)
                ));
        return summaries;
    }

    private void issueInvitation(
            InternalAdminAccount actor,
            InternalAdminAccount account
    ) {
        if (invitationTtlHours < 1 || invitationTtlHours > 168) {
            throw new IllegalStateException(
                    "ADMIN_INVITATION_TTL_HOURS must be between 1 and 168"
            );
        }
        Instant now = Instant.now();
        invitationRepository.revokeOpenInvitations(account.getId(), now);
        String rawToken = randomToken();

        InternalAdminInvitation invitation = new InternalAdminInvitation();
        invitation.setAdminAccountId(account.getId());
        invitation.setTokenHash(hashToken(rawToken));
        invitation.setCreatedBy(actor.getId());
        invitation.setExpiresAt(now.plus(invitationTtlHours, ChronoUnit.HOURS));
        InternalAdminInvitation saved = invitationRepository.saveAndFlush(invitation);

        eventPublisher.publishEvent(new InternalAdminInvitationIssuedEvent(
                account.getEmail(),
                account.getFullName(),
                account.getRole().getCode(),
                rawToken,
                saved.getExpiresAt()
        ));
    }

    private InternalAdminAccount saveInvitedAccount(InternalAdminAccount account) {
        try {
            return adminRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(
                    exception,
                    "internal_admin_accounts_email_key"
            )) {
                throw invitationConflict();
            }
            throw exception;
        }
    }

    private boolean containsConstraint(
            Throwable exception,
            String constraintName
    ) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private InternalAdminAccount requireLiveSystemAdmin(UUID actorId) {
        InternalAdminAccount actor = adminRepository.findById(actorId)
                .orElseThrow(() -> permissionDenied("Administrator account was not found"));
        if (actor.getAccountStatus() != AccountStatus.ACTIVE
                || actor.getRole() == null
                || actor.getRole().getCode() != RoleCode.SYSTEM_ADMIN) {
            throw permissionDenied("A live System Admin role is required");
        }
        return actor;
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)
                || normalized.length() > 255
                || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || normalized.endsWith("@manabihub.local")) {
            throw new BusinessException(
                    MessageCodes.INTERNAL_ADMIN_INVITATION_INVALID,
                    "A valid non-demo email is required"
            );
        }
        return normalized;
    }

    private String normalizeName(String fullName) {
        String normalized = fullName == null ? "" : fullName.trim();
        if (normalized.length() < 2 || normalized.length() > 255) {
            throw new BusinessException(
                    MessageCodes.INTERNAL_ADMIN_INVITATION_INVALID,
                    "Full name must contain between 2 and 255 characters"
            );
        }
        return normalized;
    }

    private void requireInternalRole(RoleCode roleCode) {
        if (roleCode == null || !INTERNAL_ROLES.contains(roleCode)) {
            throw new BusinessException(
                    MessageCodes.INTERNAL_ROLE_INVALID,
                    "Only internal administrator roles can be assigned"
            );
        }
    }

    private InvitationSummary toSummary(
            InternalAdminInvitation invitation,
            Instant now
    ) {
        if (invitation.getUsedAt() != null) {
            return new InvitationSummary(
                    InternalAdminInvitationStatus.ACCEPTED,
                    invitation.getExpiresAt()
            );
        }
        if (invitation.getRevokedAt() != null) {
            return new InvitationSummary(
                    InternalAdminInvitationStatus.REVOKED,
                    invitation.getExpiresAt()
            );
        }
        if (!now.isBefore(invitation.getExpiresAt())) {
            return new InvitationSummary(
                    InternalAdminInvitationStatus.EXPIRED,
                    invitation.getExpiresAt()
            );
        }
        return new InvitationSummary(
                InternalAdminInvitationStatus.PENDING,
                invitation.getExpiresAt()
        );
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        if (!StringUtils.hasText(rawToken) || rawToken.length() > 512) {
            throw invalidInvitation();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private BusinessException invitationConflict() {
        return new BusinessException(
                MessageCodes.INTERNAL_ADMIN_INVITATION_CONFLICT,
                "This administrator account cannot be invited",
                HttpStatus.CONFLICT
        );
    }

    private BusinessException invalidInvitation() {
        return new BusinessException(
                MessageCodes.INTERNAL_ADMIN_INVITATION_INVALID,
                "The invitation is invalid or expired",
                HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException permissionDenied(String message) {
        return new BusinessException(
                MessageCodes.SYSTEM_ADMIN_REQUIRED,
                message,
                HttpStatus.FORBIDDEN
        );
    }

    private String safeMetadata(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), 500));
    }
}
