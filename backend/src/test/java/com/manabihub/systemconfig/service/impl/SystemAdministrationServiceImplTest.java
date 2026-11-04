package com.manabihub.systemconfig.service.impl;

import com.manabihub.ai.service.AiCourseEligibilityService;
import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.InternalAdminInvitationStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.event.InternalAdminSessionsInvalidatedEvent;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.RoleRepository;
import com.manabihub.identity.service.InternalAdminInvitationService;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.systemconfig.entity.SystemSetting;
import com.manabihub.systemconfig.repository.SystemSettingRepository;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import com.manabihub.systemconfig.service.SystemSettingValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SystemAdministrationServiceImpl} — UC-31 Configure System Settings.
 * <p>
 * Grouped with {@code @Nested} so Surefire reports one summary line per Report 5.1 sheet:
 * <pre>
 *   SystemAdministrationServiceImplTest$UpdateSetting            -> sheet 51 updateSetting
 *   SystemAdministrationServiceImplTest$UpdateInternalAdminRole  -> sheet 52 updateInternalAdminRole
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class SystemAdministrationServiceImplTest {

    @Mock private SystemSettingRepository settingRepository;
    @Mock private InternalAdminAccountRepository adminRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private InternalAdminInvitationService invitationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;
    @Mock private AiCourseEligibilityService aiCourseEligibilityService;

    private SystemAdministrationServiceImpl service;
    private UUID actorId;
    private InternalAdminAccount actor;

    @BeforeEach
    void setUp() {
        service = new SystemAdministrationServiceImpl(
                settingRepository,
                adminRepository,
                roleRepository,
                auditLogService,
                new SystemSettingValidator(),
                new CommercialPolicyService(settingRepository),
                invitationService,
                eventPublisher,
                notificationService,
                aiCourseEligibilityService
        );
        actorId = UUID.randomUUID();
        actor = account(actorId, "system@manabihub.local", RoleCode.SYSTEM_ADMIN);
        org.mockito.Mockito.lenient()
                .when(adminRepository.findById(actorId)).thenReturn(Optional.of(actor));
        org.mockito.Mockito.lenient()
                .when(invitationService.latestInvitationSummaries(any()))
                .thenReturn(Map.of());
        org.mockito.Mockito.lenient()
                .when(adminRepository.findAllByStatusAndRoleCodeForUpdate(
                        AccountStatus.ACTIVE,
                        RoleCode.SYSTEM_ADMIN
                ))
                .thenReturn(java.util.List.of(actor));
    }

    /** COMMISSION_RATE is a policy key: allowed range [0, 1] with at most 4 decimals. */
    private SystemSetting commissionRate(String currentValue, boolean editable) {
        SystemSetting setting = SystemSetting.builder()
                .id(UUID.randomUUID())
                .settingKey("COMMISSION_RATE")
                .settingValue(currentValue)
                .valueType("NUMBER")
                .editable(editable)
                .build();
        when(settingRepository.findAllBySettingKeyInForUpdate(any()))
                .thenReturn(List.of(setting));
        return setting;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 51 — updateSetting (UC-31 Configure System Settings) — 11 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 51 - updateSetting (UC-31)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class UpdateSetting {

        @Test
        @Order(1)
        @DisplayName("UTCID01 (N) - 0.2500 normalised to 0.25, before/after audited")
        void updateSettingNormalizesAndAuditsBeforeAfter() {
            SystemSetting setting = commissionRate("0.20", true);
            when(settingRepository.save(setting)).thenReturn(setting);

            var response = service.updateSetting(
                    actorId,
                    "commission_rate",
                    "0.2500",
                    "Approved platform pricing change"
            );

            assertEquals("0.25", response.value());
            assertEquals(actorId, setting.getUpdatedBy());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
            verify(auditLogService).logAdminAction(
                    eq(actorId),
                    eq("SYSTEM_ADMIN"),
                    eq("UPDATE_SYSTEM_SETTING"),
                    eq("SYSTEM_SETTING"),
                    eq(setting.getId()),
                    before.capture(),
                    after.capture(),
                    any()
            );
            assertEquals("0.20", before.getValue().get("value"));
            assertEquals("0.25", after.getValue().get("value"));
        }

        @Test
        @Order(2)
        @DisplayName("UTCID02 (N) - unchanged value -> no write, no audit, no notification")
        void updatingWithTheSameValueIsANoOp() {
            commissionRate("0.2", true);

            var response = service.updateSetting(
                    actorId, "COMMISSION_RATE", "0.20", "No real change");

            assertEquals("0.2", response.value());
            verify(settingRepository, never()).save(any());
            verify(auditLogService, never()).logAdminAction(
                    any(), any(), any(), any(), any(), any(), any(), any());
            verify(notificationService, never()).createNotificationForAdminRole(
                    any(), any(), any(), any(), any());
        }

        @Test
        @Order(3)
        @DisplayName("UTCID03 (B) - value 0 = lower bound -> accepted")
        void lowerBoundValueIsAccepted() {
            SystemSetting setting = commissionRate("0.20", true);
            when(settingRepository.save(setting)).thenReturn(setting);

            var response = service.updateSetting(actorId, "COMMISSION_RATE", "0", "Zero commission");

            assertEquals("0", response.value());
        }

        @Test
        @Order(4)
        @DisplayName("UTCID04 (B) - value 1 = upper bound -> accepted")
        void upperBoundValueIsAccepted() {
            SystemSetting setting = commissionRate("0.20", true);
            when(settingRepository.save(setting)).thenReturn(setting);

            var response = service.updateSetting(actorId, "COMMISSION_RATE", "1", "Full commission");

            assertEquals("1", response.value());
        }

        @Test
        @Order(5)
        @DisplayName("UTCID05 (B) - value -0.01 below the lower bound -> SYSTEM_SETTING_INVALID")
        void valueBelowTheLowerBoundIsRejected() {
            commissionRate("0.20", true);

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateSetting(actorId, "COMMISSION_RATE", "-0.01", "bad")
            );

            assertEquals(MessageCodes.SYSTEM_SETTING_INVALID, error.getMessageCode());
            verify(settingRepository, never()).save(any());
        }

        @Test
        @Order(6)
        @DisplayName("UTCID06 (B) - value 5 above the upper bound -> SYSTEM_SETTING_INVALID")
        void invalidSettingIsRejectedWithoutWriteOrAudit() {
            commissionRate("0.20", true);

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateSetting(actorId, "COMMISSION_RATE", "5", "bad")
            );

            assertEquals(MessageCodes.SYSTEM_SETTING_INVALID, error.getMessageCode());
            verify(settingRepository, never()).save(any());
            verify(auditLogService, never()).logAdminAction(
                    any(), any(), any(), any(), any(), any(), any(), any()
            );
        }

        @Test
        @Order(7)
        @DisplayName("UTCID07 (A) - non-numeric value -> SYSTEM_SETTING_INVALID")
        void nonNumericValueIsRejected() {
            commissionRate("0.20", true);

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateSetting(actorId, "COMMISSION_RATE", "abc", "bad")
            );

            assertEquals(MessageCodes.SYSTEM_SETTING_INVALID, error.getMessageCode());
            verify(settingRepository, never()).save(any());
        }

        @Test
        @Order(8)
        @DisplayName("UTCID08 (A) - setting key not found -> COMMON_NOT_FOUND")
        void unknownSettingKeyIsRejected() {
            when(settingRepository.findAllBySettingKeyInForUpdate(any()))
                    .thenReturn(List.of());

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateSetting(actorId, "COMMISSION_RATE", "0.25", "missing")
            );

            assertEquals(MessageCodes.COMMON_NOT_FOUND, error.getMessageCode());
            verify(settingRepository, never()).save(any());
        }

        @Test
        @Order(9)
        @DisplayName("UTCID09 (A) - read-only setting -> SYSTEM_SETTING_NOT_EDITABLE")
        void readOnlySettingCannotBeChanged() {
            commissionRate("0.20", false);

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateSetting(actorId, "COMMISSION_RATE", "0.25", "locked")
            );

            assertEquals(MessageCodes.SYSTEM_SETTING_NOT_EDITABLE, error.getMessageCode());
            verify(settingRepository, never()).save(any());
        }

        @Test
        @Order(10)
        @DisplayName("UTCID10 (A) - actor is COURSE_MANAGER -> SYSTEM_ADMIN_REQUIRED")
        void actorWithoutTheSystemAdminRoleIsRejected() {
            actor.setRole(role(RoleCode.COURSE_MANAGER));

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateSetting(actorId, "COMMISSION_RATE", "0.25", "no rights")
            );

            assertEquals(MessageCodes.SYSTEM_ADMIN_REQUIRED, error.getMessageCode());
            assertEquals(403, error.getHttpStatus().value());
            verify(settingRepository, never()).findAllBySettingKeyInForUpdate(any());
        }

        @Test
        @Order(11)
        @DisplayName("UTCID11 (A) - actor account not found -> SYSTEM_ADMIN_REQUIRED")
        void unknownActorIsRejected() {
            UUID strangerId = UUID.randomUUID();
            when(adminRepository.findById(strangerId)).thenReturn(Optional.empty());

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateSetting(strangerId, "COMMISSION_RATE", "0.25", "no account")
            );

            assertEquals(MessageCodes.SYSTEM_ADMIN_REQUIRED, error.getMessageCode());
            verify(settingRepository, never()).save(any());
        }

        @Test
        @Order(12)
        @DisplayName("AI price floor update synchronizes persisted course AI flags")
        void updatingAiPriceFloorSynchronizesCourseFlags() {
            SystemSetting setting = SystemSetting.builder()
                    .id(UUID.randomUUID())
                    .settingKey("AI_SUPPORT_PRICE_FLOOR")
                    .settingValue("100000")
                    .valueType("NUMBER")
                    .editable(true)
                    .build();
            when(settingRepository.findBySettingKeyForUpdate("AI_SUPPORT_PRICE_FLOOR"))
                    .thenReturn(Optional.of(setting));
            when(settingRepository.save(setting)).thenReturn(setting);

            service.updateSetting(actorId, "AI_SUPPORT_PRICE_FLOOR", "0", "Enable AI for free courses");

            verify(aiCourseEligibilityService).synchronizeAllCourses(BigDecimal.ZERO);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 52 — updateInternalAdminRole (UC-31 Configure System Settings) — 9 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 52 - updateInternalAdminRole (UC-31)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class UpdateInternalAdminRole {

        @Test
        @Order(1)
        @DisplayName("UTCID01 (N) - COURSE_MANAGER -> FINANCE_MANAGER, audit + sessions invalidated")
        void roleAssignmentChangesOnlyTargetAndCreatesAudit() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId,
                    "course@manabihub.local",
                    RoleCode.COURSE_MANAGER
            );
            Role financeRole = role(RoleCode.FINANCE_MANAGER);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));
            when(roleRepository.findByCode(RoleCode.FINANCE_MANAGER))
                    .thenReturn(Optional.of(financeRole));
            when(adminRepository.save(target)).thenReturn(target);

            var response = service.updateInternalAdminRole(
                    actorId,
                    targetId,
                    RoleCode.FINANCE_MANAGER,
                    "Move ownership to finance"
            );

            assertEquals(RoleCode.FINANCE_MANAGER, response.role());
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            InternalAdminSessionsInvalidatedEvent invalidated =
                    (InternalAdminSessionsInvalidatedEvent) eventCaptor.getValue();
            assertEquals(targetId, invalidated.adminAccountId());
            assertEquals("ROLE_CHANGED", invalidated.reason());
            verify(auditLogService).logAdminAction(
                    eq(actorId),
                    eq("SYSTEM_ADMIN"),
                    eq("ASSIGN_INTERNAL_ROLE"),
                    eq("INTERNAL_ADMIN_ACCOUNT"),
                    eq(targetId),
                    eq(Map.of("role", "COURSE_MANAGER")),
                    eq(Map.of("role", "FINANCE_MANAGER")),
                    any()
            );
        }

        @Test
        @Order(2)
        @DisplayName("UTCID02 (N) - target already has that role -> no write, no session reset")
        void assigningTheRoleTheTargetAlreadyHasIsANoOp() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId, "course@manabihub.local", RoleCode.COURSE_MANAGER);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));

            var response = service.updateInternalAdminRole(
                    actorId, targetId, RoleCode.COURSE_MANAGER, "Same role");

            assertEquals(RoleCode.COURSE_MANAGER, response.role());
            verify(adminRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
            verify(auditLogService, never()).logAdminAction(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @Order(3)
        @DisplayName("UTCID03 (A) - actor changes own role -> INTERNAL_ROLE_SELF_ASSIGNMENT_FORBIDDEN")
        void systemAdminCannotChangeOwnRole() {
            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminRole(
                            actorId,
                            actorId,
                            RoleCode.COURSE_MANAGER,
                            "self change"
                    )
            );
            assertEquals(
                    MessageCodes.INTERNAL_ROLE_SELF_ASSIGNMENT_FORBIDDEN,
                    error.getMessageCode()
            );
            verify(adminRepository, never()).findByIdForRoleUpdate(any());
        }

        @Test
        @Order(4)
        @DisplayName("UTCID04 (A) - STUDENT is not an internal role -> INTERNAL_ROLE_INVALID")
        void nonInternalRoleCannotBeAssigned() {
            UUID targetId = UUID.randomUUID();

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminRole(
                            actorId, targetId, RoleCode.STUDENT, "wrong role")
            );

            assertEquals(MessageCodes.INTERNAL_ROLE_INVALID, error.getMessageCode());
            verify(adminRepository, never()).findByIdForRoleUpdate(any());
        }

        @Test
        @Order(5)
        @DisplayName("UTCID05 (A) - target account not found -> COMMON_NOT_FOUND")
        void unknownTargetIsRejected() {
            UUID targetId = UUID.randomUUID();
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.empty());

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminRole(
                            actorId, targetId, RoleCode.FINANCE_MANAGER, "missing")
            );

            assertEquals(MessageCodes.COMMON_NOT_FOUND, error.getMessageCode());
            verify(adminRepository, never()).save(any());
        }

        @Test
        @Order(6)
        @DisplayName("UTCID06 (A) - target account not ACTIVE -> INTERNAL_ROLE_INVALID")
        void inactiveTargetCannotBeAssignedARole() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId, "course@manabihub.local", RoleCode.COURSE_MANAGER);
            target.setAccountStatus(AccountStatus.DISABLED);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminRole(
                            actorId, targetId, RoleCode.FINANCE_MANAGER, "inactive")
            );

            assertEquals(MessageCodes.INTERNAL_ROLE_INVALID, error.getMessageCode());
            verify(adminRepository, never()).save(any());
        }

        @Test
        @Order(7)
        @DisplayName("UTCID07 (A) - demoting the last System Admin -> LAST_SYSTEM_ADMIN_REQUIRED")
        void theLastSystemAdminCannotBeDemoted() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId, "admin2@manabihub.local", RoleCode.SYSTEM_ADMIN);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));
            when(adminRepository.findAllByStatusAndRoleCodeForUpdate(
                    AccountStatus.ACTIVE, RoleCode.SYSTEM_ADMIN))
                    .thenReturn(List.of(target));

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminRole(
                            actorId, targetId, RoleCode.COURSE_MANAGER, "demote last admin")
            );

            assertEquals(MessageCodes.LAST_SYSTEM_ADMIN_REQUIRED, error.getMessageCode());
            verify(adminRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @Order(8)
        @DisplayName("UTCID08 (A) - role row missing in the database -> INTERNAL_ROLE_INVALID")
        void unconfiguredRoleIsRejected() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId, "course@manabihub.local", RoleCode.COURSE_MANAGER);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));
            when(roleRepository.findByCode(RoleCode.FINANCE_MANAGER))
                    .thenReturn(Optional.empty());

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminRole(
                            actorId, targetId, RoleCode.FINANCE_MANAGER, "role not configured")
            );

            assertEquals(MessageCodes.INTERNAL_ROLE_INVALID, error.getMessageCode());
            verify(adminRepository, never()).save(any());
        }

        @Test
        @Order(9)
        @DisplayName("UTCID09 (A) - actor is COURSE_MANAGER -> SYSTEM_ADMIN_REQUIRED")
        void actorWithoutTheSystemAdminRoleCannotAssignRoles() {
            actor.setRole(role(RoleCode.COURSE_MANAGER));
            UUID targetId = UUID.randomUUID();

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminRole(
                            actorId, targetId, RoleCode.FINANCE_MANAGER, "no rights")
            );

            assertEquals(MessageCodes.SYSTEM_ADMIN_REQUIRED, error.getMessageCode());
            verify(adminRepository, never()).findByIdForRoleUpdate(any());
        }
    }

    @Nested
    @DisplayName("updateInternalAdminStatus")
    class UpdateInternalAdminStatus {

        @Test
        void disablingAnActiveAccountInvalidatesAccessAndCreatesAudit() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId, "course@example.com", RoleCode.COURSE_MANAGER);
            target.setCredentialVersion(4);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));
            when(adminRepository.save(target)).thenReturn(target);

            var response = service.updateInternalAdminStatus(
                    actorId, targetId, AccountStatus.DISABLED, "Staff member left the team");

            assertEquals(AccountStatus.DISABLED, response.status());
            assertEquals(5, target.getCredentialVersion());
            verify(invitationService).revokeOpenInvitations(eq(targetId), any());
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            InternalAdminSessionsInvalidatedEvent event =
                    (InternalAdminSessionsInvalidatedEvent) eventCaptor.getValue();
            assertEquals(targetId, event.adminAccountId());
            assertEquals("ACCESS_DISABLED", event.reason());
            verify(auditLogService).logAdminAction(
                    eq(actorId),
                    eq("SYSTEM_ADMIN"),
                    eq("DISABLE_INTERNAL_ADMIN_ACCOUNT"),
                    eq("INTERNAL_ADMIN_ACCOUNT"),
                    eq(targetId),
                    eq(Map.of("status", "ACTIVE")),
                    eq(Map.of("status", "DISABLED")),
                    any()
            );
        }

        @Test
        void aPreviouslyActiveAccountCanBeReactivated() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId, "finance@example.com", RoleCode.FINANCE_MANAGER);
            target.setAccountStatus(AccountStatus.DISABLED);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));
            when(adminRepository.save(target)).thenReturn(target);

            var response = service.updateInternalAdminStatus(
                    actorId, targetId, AccountStatus.ACTIVE, "Team member returned");

            assertEquals(AccountStatus.ACTIVE, response.status());
            verify(invitationService, never()).revokeOpenInvitations(any(), any());
            verify(eventPublisher).publishEvent(any(InternalAdminSessionsInvalidatedEvent.class));
        }

        @Test
        void anUnacceptedInvitationCannotBeActivatedByAnAdministrator() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId, "invited@example.com", RoleCode.COURSE_MANAGER);
            target.setAccountStatus(AccountStatus.DISABLED);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));
            when(invitationService.latestInvitationSummaries(any())).thenReturn(Map.of(
                    targetId,
                    new InternalAdminInvitationService.InvitationSummary(
                            InternalAdminInvitationStatus.PENDING,
                            java.time.Instant.now().plusSeconds(3600)
                    )
            ));

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminStatus(
                            actorId, targetId, AccountStatus.ACTIVE, "Activate manually")
            );

            assertEquals(MessageCodes.INTERNAL_ADMIN_STATUS_INVALID, error.getMessageCode());
            verify(adminRepository, never()).save(any());
        }

        @Test
        void aBuiltInDemoAccountCannotBeReactivatedOutsideLocalProfile() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId, "course.manager@manabihub.local", RoleCode.COURSE_MANAGER);
            target.setAccountStatus(AccountStatus.DISABLED);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminStatus(
                            actorId, targetId, AccountStatus.ACTIVE, "Enable demo in production")
            );

            assertEquals(MessageCodes.INTERNAL_ADMIN_STATUS_INVALID, error.getMessageCode());
            verify(adminRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void anAdministratorCannotDisableTheirOwnAccount() {
            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminStatus(
                            actorId, actorId, AccountStatus.DISABLED, "Disable myself")
            );

            assertEquals(
                    MessageCodes.INTERNAL_ADMIN_SELF_DISABLE_FORBIDDEN,
                    error.getMessageCode()
            );
            verify(adminRepository, never()).findByIdForRoleUpdate(any());
        }

        @Test
        void theLastActiveSystemAdminCannotBeDisabled() {
            UUID targetId = UUID.randomUUID();
            InternalAdminAccount target = account(
                    targetId, "other-admin@example.com", RoleCode.SYSTEM_ADMIN);
            when(adminRepository.findByIdForRoleUpdate(targetId)).thenReturn(Optional.of(target));
            when(adminRepository.findAllByStatusAndRoleCodeForUpdate(
                    AccountStatus.ACTIVE, RoleCode.SYSTEM_ADMIN))
                    .thenReturn(List.of(target));

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminStatus(
                            actorId, targetId, AccountStatus.DISABLED, "Remove final admin")
            );

            assertEquals(MessageCodes.LAST_SYSTEM_ADMIN_REQUIRED, error.getMessageCode());
            verify(adminRepository, never()).save(any());
        }

        @Test
        void lockedIsNotAnAllowedManagedStatus() {
            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.updateInternalAdminStatus(
                            actorId, UUID.randomUUID(), AccountStatus.LOCKED, "Wrong status")
            );

            assertEquals(MessageCodes.INTERNAL_ADMIN_STATUS_INVALID, error.getMessageCode());
            verify(adminRepository, never()).findByIdForRoleUpdate(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Not part of Report 5.1 — kept from the earlier iteration
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("(khong thuoc sheet nao) - listInternalAdmins")
    class ListInternalAdmins {

        @Test
        void databaseRoleIsAuthoritativeEvenWhenJwtLayerWasStale() {
            actor.setRole(role(RoleCode.COURSE_MANAGER));

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.listInternalAdmins(actorId)
            );

            assertEquals(MessageCodes.SYSTEM_ADMIN_REQUIRED, error.getMessageCode());
            assertEquals(403, error.getHttpStatus().value());
        }
    }

    // ──────────────────────────────────────────────
    // Fixtures
    // ──────────────────────────────────────────────

    private InternalAdminAccount account(UUID id, String email, RoleCode roleCode) {
        InternalAdminAccount account = new InternalAdminAccount();
        account.setId(id);
        account.setEmail(email);
        account.setFullName(email);
        account.setPasswordHash("not-returned");
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setRole(role(roleCode));
        return account;
    }

    private Role role(RoleCode code) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setCode(code);
        role.setName(code.name());
        return role;
    }
}
