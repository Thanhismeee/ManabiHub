package com.manabihub.systemconfig.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.ai.service.AiCourseEligibilityService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.InternalAdminInvitationStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.event.InternalAdminSessionsInvalidatedEvent;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.identity.repository.RoleRepository;
import com.manabihub.identity.service.InternalAdminInvitationService;
import com.manabihub.systemconfig.dto.response.InternalAdminAccountResponse;
import com.manabihub.systemconfig.dto.response.SystemSettingResponse;
import com.manabihub.systemconfig.entity.SystemSetting;
import com.manabihub.systemconfig.repository.SystemSettingRepository;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import com.manabihub.systemconfig.service.SystemAdministrationService;
import com.manabihub.systemconfig.service.SystemSettingValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SystemAdministrationServiceImpl implements SystemAdministrationService {

    private static final List<RoleCode> INTERNAL_ROLES = List.of(
            RoleCode.SYSTEM_ADMIN,
            RoleCode.COURSE_MANAGER,
            RoleCode.FINANCE_MANAGER
    );

    private final SystemSettingRepository settingRepository;
    private final InternalAdminAccountRepository adminRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final SystemSettingValidator validator;
    private final CommercialPolicyService commercialPolicyService;
    private final InternalAdminInvitationService invitationService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final AiCourseEligibilityService aiCourseEligibilityService;

    @Override
    @Transactional(readOnly = true)
    public List<SystemSettingResponse> listSettings(UUID actorId) {
        requireLiveSystemAdmin(actorId);
        return settingRepository
                .findAllBySettingKeyInOrderBySettingKeyAsc(SystemSettingValidator.SUPPORTED_KEYS)
                .stream()
                .map(this::toSettingResponse)
                .toList();
    }

    @Override
    @Transactional
    public SystemSettingResponse updateSetting(
            UUID actorId,
            String settingKey,
            String value,
            String reason
    ) {
        InternalAdminAccount actor = requireLiveSystemAdmin(actorId);
        String key = settingKey == null ? "" : settingKey.trim().toUpperCase();
        List<SystemSetting> lockedPolicySettings = List.of();
        SystemSetting setting;
        if (commercialPolicyService.isPolicyKey(key)) {
            lockedPolicySettings = settingRepository.findAllBySettingKeyInForUpdate(
                    commercialPolicyService.policyKeys());
            setting = lockedPolicySettings.stream()
                    .filter(candidate -> candidate.getSettingKey().equals(key))
                    .findFirst()
                    .orElseThrow(this::settingNotFound);
        } else {
            setting = settingRepository.findBySettingKeyForUpdate(key)
                    .orElseThrow(this::settingNotFound);
        }

        if (!setting.isEditable()) {
            throw new BusinessException(
                    MessageCodes.SYSTEM_SETTING_NOT_EDITABLE,
                    "This system setting is read-only",
                    HttpStatus.CONFLICT
            );
        }

        String normalizedValue = validator.normalize(key, value);
        commercialPolicyService.validateCandidate(
                lockedPolicySettings,
                key,
                normalizedValue);
        String previousValue = setting.getSettingValue();
        if (previousValue.equals(normalizedValue)) {
            synchronizeAiCourseFlags(key, normalizedValue);
            return toSettingResponse(setting);
        }

        setting.setSettingValue(normalizedValue);
        setting.setUpdatedBy(actorId);
        SystemSetting saved = settingRepository.save(setting);
        synchronizeAiCourseFlags(key, normalizedValue);

        auditLogService.logAdminAction(
                actorId,
                actor.getRole().getCode().name(),
                "UPDATE_SYSTEM_SETTING",
                "SYSTEM_SETTING",
                saved.getId(),
                Map.of("settingKey", key, "value", previousValue),
                Map.of("settingKey", key, "value", normalizedValue),
                Map.of("reason", reason.trim())
        );
        notificationService.createNotificationForAdminRole(
                RoleCode.SYSTEM_ADMIN.name(),
                "Cấu hình hệ thống đã được cập nhật",
                "Thiết lập \"" + key + "\" vừa được thay đổi. Lý do: " + reason.trim(),
                NotificationTypes.SYSTEM_SETTING_CHANGED,
                "/admin/settings"
        );
        return toSettingResponse(saved);
    }

    private void synchronizeAiCourseFlags(String key, String normalizedValue) {
        if ("AI_SUPPORT_PRICE_FLOOR".equals(key)) {
            aiCourseEligibilityService.synchronizeAllCourses(new BigDecimal(normalizedValue));
        }
    }

    private BusinessException settingNotFound() {
        return new BusinessException(
                MessageCodes.COMMON_NOT_FOUND,
                "System setting not found",
                HttpStatus.NOT_FOUND
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalAdminAccountResponse> listInternalAdmins(UUID actorId) {
        requireLiveSystemAdmin(actorId);
        List<InternalAdminAccount> accounts = adminRepository.findAllByOrderByFullNameAsc();
        Map<UUID, InternalAdminInvitationService.InvitationSummary> invitations =
                invitationService.latestInvitationSummaries(
                        accounts.stream().map(InternalAdminAccount::getId).toList()
                );
        return accounts.stream()
                .map(account -> toAdminResponse(
                        account,
                        invitations.getOrDefault(
                                account.getId(),
                                InternalAdminInvitationService.InvitationSummary.none()
                        )
                ))
                .toList();
    }

    @Override
    @Transactional
    public InternalAdminAccountResponse inviteInternalAdmin(
            UUID actorId,
            String email,
            String fullName,
            RoleCode roleCode,
            String reason
    ) {
        InternalAdminAccount account = invitationService.invite(
                actorId,
                email,
                fullName,
                roleCode,
                reason
        );
        return toAdminResponse(
                account,
                invitationService.latestInvitationSummaries(List.of(account.getId()))
                        .getOrDefault(
                                account.getId(),
                                InternalAdminInvitationService.InvitationSummary.none()
                        )
        );
    }

    @Override
    @Transactional
    public InternalAdminAccountResponse resendInternalAdminInvitation(
            UUID actorId,
            UUID targetAdminId,
            String reason
    ) {
        InternalAdminAccount account = invitationService.resend(
                actorId,
                targetAdminId,
                reason
        );
        return toAdminResponse(
                account,
                invitationService.latestInvitationSummaries(List.of(account.getId()))
                        .getOrDefault(
                                account.getId(),
                                InternalAdminInvitationService.InvitationSummary.none()
                        )
        );
    }

    @Override
    @Transactional
    public InternalAdminAccountResponse updateInternalAdminRole(
            UUID actorId,
            UUID targetAdminId,
            RoleCode roleCode,
            String reason
    ) {
        InternalAdminAccount actor = requireLiveSystemAdmin(actorId);

        if (actorId.equals(targetAdminId)) {
            throw new BusinessException(
                    MessageCodes.INTERNAL_ROLE_SELF_ASSIGNMENT_FORBIDDEN,
                    "Use another active System Admin to change your own role",
                    HttpStatus.CONFLICT
            );
        }
        if (!INTERNAL_ROLES.contains(roleCode)) {
            throw new BusinessException(
                    MessageCodes.INTERNAL_ROLE_INVALID,
                    "Only internal administrator roles can be assigned"
            );
        }

        // Serialize role assignments against the active System Admin set so two
        // concurrent demotions cannot both observe "another admin" and leave
        // the platform with zero System Admins.
        List<InternalAdminAccount> activeSystemAdmins =
                adminRepository.findAllByStatusAndRoleCodeForUpdate(
                        AccountStatus.ACTIVE,
                        RoleCode.SYSTEM_ADMIN
                );

        InternalAdminAccount target = adminRepository.findByIdForRoleUpdate(targetAdminId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Internal administrator account not found",
                        HttpStatus.NOT_FOUND
                ));
        if (target.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(
                    MessageCodes.INTERNAL_ROLE_INVALID,
                    "Only active administrator accounts can be assigned a role",
                    HttpStatus.CONFLICT
            );
        }

        RoleCode previousRole = target.getRole().getCode();
        if (previousRole == roleCode) {
            return toAdminResponse(target, invitationSummary(target.getId()));
        }

        if (previousRole == RoleCode.SYSTEM_ADMIN && activeSystemAdmins.size() <= 1) {
            throw new BusinessException(
                    MessageCodes.LAST_SYSTEM_ADMIN_REQUIRED,
                    "At least one active System Admin must remain",
                    HttpStatus.CONFLICT
            );
        }

        Role newRole = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.INTERNAL_ROLE_INVALID,
                        "Internal role is not configured"
                ));

        target.setRole(newRole);
        target.setCredentialVersion(target.getCredentialVersion() + 1);
        InternalAdminAccount saved = adminRepository.save(target);
        eventPublisher.publishEvent(new InternalAdminSessionsInvalidatedEvent(
                targetAdminId,
                "ROLE_CHANGED",
                Instant.now()
        ));

        auditLogService.logAdminAction(
                actorId,
                actor.getRole().getCode().name(),
                "ASSIGN_INTERNAL_ROLE",
                "INTERNAL_ADMIN_ACCOUNT",
                targetAdminId,
                Map.of("role", previousRole.name()),
                Map.of("role", roleCode.name()),
                Map.of(
                        "reason", reason.trim(),
                        "targetEmail", target.getEmail(),
                        "requiresReauthentication", true
                )
        );
        notificationService.createAdminNotification(
                saved.getId(),
                saved.getEmail(),
                "Vai trò quản trị đã được thay đổi",
                "Vai trò của tài khoản đã chuyển từ " + roleLabel(previousRole)
                        + " sang " + roleLabel(roleCode) + ". Vui lòng đăng nhập lại để tiếp tục.",
                NotificationTypes.ADMIN_ROLE_CHANGED,
                "/admin/dashboard"
        );
        return toAdminResponse(saved, invitationSummary(saved.getId()));
    }

    @Override
    @Transactional
    public InternalAdminAccountResponse updateInternalAdminStatus(
            UUID actorId,
            UUID targetAdminId,
            AccountStatus status,
            String reason
    ) {
        InternalAdminAccount actor = requireLiveSystemAdmin(actorId);
        if (status != AccountStatus.ACTIVE && status != AccountStatus.DISABLED) {
            throw invalidAdminStatus("Only ACTIVE and DISABLED statuses are supported");
        }
        if (actorId.equals(targetAdminId) && status == AccountStatus.DISABLED) {
            throw new BusinessException(
                    MessageCodes.INTERNAL_ADMIN_SELF_DISABLE_FORBIDDEN,
                    "Use another active System Admin to change your own access status",
                    HttpStatus.CONFLICT
            );
        }

        List<InternalAdminAccount> activeSystemAdmins =
                adminRepository.findAllByStatusAndRoleCodeForUpdate(
                        AccountStatus.ACTIVE,
                        RoleCode.SYSTEM_ADMIN
                );
        InternalAdminAccount target = adminRepository.findByIdForRoleUpdate(targetAdminId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Internal administrator account not found",
                        HttpStatus.NOT_FOUND
                ));
        if (target.getRole() == null || !INTERNAL_ROLES.contains(target.getRole().getCode())) {
            throw invalidAdminStatus("The target does not have an internal administrator role");
        }
        if (target.getAccountStatus() == status) {
            return toAdminResponse(target, invitationSummary(target.getId()));
        }
        if (target.getAccountStatus() != AccountStatus.ACTIVE
                && target.getAccountStatus() != AccountStatus.DISABLED) {
            throw invalidAdminStatus("Only active or disabled accounts can be changed here");
        }

        InternalAdminInvitationService.InvitationSummary invitation =
                invitationSummary(target.getId());
        if (status == AccountStatus.ACTIVE
                && invitation.status()
                != InternalAdminInvitationStatus.ACCEPTED
                && invitation.status()
                != InternalAdminInvitationStatus.NONE) {
            throw invalidAdminStatus(
                    "An invited administrator must set a password using a valid invitation"
            );
        }
        if (status == AccountStatus.ACTIVE
                && target.getEmail().endsWith("@manabihub.local")) {
            throw invalidAdminStatus(
                    "Built-in demo accounts can only be enabled by the local development profile"
            );
        }
        if (status == AccountStatus.DISABLED
                && target.getRole().getCode() == RoleCode.SYSTEM_ADMIN
                && activeSystemAdmins.size() <= 1) {
            throw new BusinessException(
                    MessageCodes.LAST_SYSTEM_ADMIN_REQUIRED,
                    "At least one active System Admin must remain",
                    HttpStatus.CONFLICT
            );
        }

        AccountStatus previousStatus = target.getAccountStatus();
        Instant changedAt = Instant.now();
        target.setAccountStatus(status);
        target.setCredentialVersion(target.getCredentialVersion() + 1);
        InternalAdminAccount saved = adminRepository.save(target);
        if (status == AccountStatus.DISABLED) {
            invitationService.revokeOpenInvitations(targetAdminId, changedAt);
        }
        eventPublisher.publishEvent(new InternalAdminSessionsInvalidatedEvent(
                targetAdminId,
                status == AccountStatus.DISABLED ? "ACCESS_DISABLED" : "ACCESS_REACTIVATED",
                changedAt
        ));

        auditLogService.logAdminAction(
                actorId,
                actor.getRole().getCode().name(),
                status == AccountStatus.DISABLED
                        ? "DISABLE_INTERNAL_ADMIN_ACCOUNT"
                        : "REACTIVATE_INTERNAL_ADMIN_ACCOUNT",
                "INTERNAL_ADMIN_ACCOUNT",
                targetAdminId,
                Map.of("status", previousStatus.name()),
                Map.of("status", status.name()),
                Map.of(
                        "reason", reason.trim(),
                        "targetEmail", target.getEmail(),
                        "sessionsInvalidated", true
                )
        );
        return toAdminResponse(saved, invitationSummary(saved.getId()));
    }

    private BusinessException invalidAdminStatus(String message) {
        return new BusinessException(
                MessageCodes.INTERNAL_ADMIN_STATUS_INVALID,
                message,
                HttpStatus.CONFLICT
        );
    }

    private String roleLabel(RoleCode roleCode) {
        return switch (roleCode) {
            case STUDENT -> "Học viên";
            case TEACHER -> "Giảng viên";
            case SYSTEM_ADMIN -> "Quản trị hệ thống";
            case COURSE_MANAGER -> "Quản lý khóa học";
            case FINANCE_MANAGER -> "Quản lý tài chính";
        };
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

    private BusinessException permissionDenied(String message) {
        return new BusinessException(
                MessageCodes.SYSTEM_ADMIN_REQUIRED,
                message,
                HttpStatus.FORBIDDEN
        );
    }

    private SystemSettingResponse toSettingResponse(SystemSetting setting) {
        return new SystemSettingResponse(
                setting.getId(),
                setting.getSettingKey(),
                setting.getSettingValue(),
                setting.getValueType(),
                setting.getDescription(),
                setting.isEditable(),
                setting.getUpdatedBy(),
                setting.getUpdatedAt()
        );
    }

    private InternalAdminInvitationService.InvitationSummary invitationSummary(UUID accountId) {
        return invitationService.latestInvitationSummaries(List.of(accountId))
                .getOrDefault(
                        accountId,
                        InternalAdminInvitationService.InvitationSummary.none()
                );
    }

    private InternalAdminAccountResponse toAdminResponse(
            InternalAdminAccount account,
            InternalAdminInvitationService.InvitationSummary invitation
    ) {
        return new InternalAdminAccountResponse(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                account.getAccountStatus(),
                account.getRole().getCode(),
                account.getLastLoginAt(),
                account.getUpdatedAt(),
                invitation.status(),
                invitation.expiresAt()
        );
    }
}
