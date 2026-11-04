package com.manabihub.identity.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.InternalAdminInvitation;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.event.InternalAdminInvitationIssuedEvent;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.InternalAdminInvitationRepository;
import com.manabihub.identity.repository.RoleRepository;
import com.manabihub.identity.service.InternalAdminPasswordPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAdminInvitationServiceImplTest {

    @Mock private InternalAdminAccountRepository adminRepository;
    @Mock private InternalAdminInvitationRepository invitationRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InternalAdminInvitationServiceImpl service;
    private BCryptPasswordEncoder passwordEncoder;
    private InternalAdminAccount actor;
    private Role courseManagerRole;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new InternalAdminInvitationServiceImpl(
                adminRepository,
                invitationRepository,
                roleRepository,
                passwordEncoder,
                new InternalAdminPasswordPolicy(),
                auditLogService,
                eventPublisher
        );
        ReflectionTestUtils.setField(service, "invitationTtlHours", 24L);

        actor = account(UUID.randomUUID(), "system@example.com", RoleCode.SYSTEM_ADMIN);
        courseManagerRole = role(RoleCode.COURSE_MANAGER);
    }

    @Test
    void inviteCreatesDisabledAccountWithHashedOneTimeToken() {
        when(adminRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(adminRepository.findByEmailIgnoreCaseForUpdate("manager@example.com"))
                .thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.COURSE_MANAGER))
                .thenReturn(Optional.of(courseManagerRole));
        when(adminRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InternalAdminAccount account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });
        when(invitationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InternalAdminInvitation invitation = invocation.getArgument(0);
            invitation.setId(UUID.randomUUID());
            return invitation;
        });

        InternalAdminAccount invited = service.invite(
                actor.getId(),
                " Manager@Example.com ",
                " Course Manager ",
                RoleCode.COURSE_MANAGER,
                "Approved staffing request"
        );

        assertEquals("manager@example.com", invited.getEmail());
        assertEquals("Course Manager", invited.getFullName());
        assertEquals(AccountStatus.DISABLED, invited.getAccountStatus());
        assertEquals(RoleCode.COURSE_MANAGER, invited.getRole().getCode());
        assertFalse(invited.getPasswordHash().isBlank());

        ArgumentCaptor<InternalAdminInvitation> invitationCaptor =
                ArgumentCaptor.forClass(InternalAdminInvitation.class);
        verify(invitationRepository).saveAndFlush(invitationCaptor.capture());
        InternalAdminInvitation persisted = invitationCaptor.getValue();
        assertEquals(64, persisted.getTokenHash().length());
        assertEquals(actor.getId(), persisted.getCreatedBy());

        ArgumentCaptor<InternalAdminInvitationIssuedEvent> eventCaptor =
                ArgumentCaptor.forClass(InternalAdminInvitationIssuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        InternalAdminInvitationIssuedEvent event = eventCaptor.getValue();
        assertNotEquals(event.rawToken(), persisted.getTokenHash());
        assertEquals("manager@example.com", event.email());
        assertEquals(RoleCode.COURSE_MANAGER, event.role());
    }

    @Test
    void activeAccountCannotBeOverwrittenByInvitation() {
        when(adminRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        InternalAdminAccount existing = account(
                UUID.randomUUID(),
                "manager@example.com",
                RoleCode.COURSE_MANAGER
        );
        when(adminRepository.findByEmailIgnoreCaseForUpdate("manager@example.com"))
                .thenReturn(Optional.of(existing));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.invite(
                        actor.getId(),
                        "manager@example.com",
                        "Manager",
                        RoleCode.FINANCE_MANAGER,
                        "Role replacement"
                )
        );

        assertEquals(MessageCodes.INTERNAL_ADMIN_INVITATION_CONFLICT, error.getMessageCode());
        verify(adminRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void acceptActivatesAccountAndConsumesInvitationExactlyOnce() {
        String rawToken = "valid-one-time-token";
        InternalAdminAccount invited = account(
                UUID.randomUUID(),
                "manager@example.com",
                RoleCode.COURSE_MANAGER
        );
        invited.setAccountStatus(AccountStatus.DISABLED);
        invited.setPasswordHash(passwordEncoder.encode("unknown-placeholder"));

        InternalAdminInvitation invitation = new InternalAdminInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setAdminAccountId(invited.getId());
        invitation.setTokenHash(sha256(rawToken));
        invitation.setCreatedBy(actor.getId());
        invitation.setExpiresAt(java.time.Instant.now().plusSeconds(3600));

        when(invitationRepository.findByTokenHash(invitation.getTokenHash()))
                .thenReturn(Optional.of(invitation));
        when(invitationRepository.findByTokenHashForUpdate(invitation.getTokenHash()))
                .thenReturn(Optional.of(invitation));
        when(adminRepository.findByIdForRoleUpdate(invited.getId()))
                .thenReturn(Optional.of(invited));

        service.accept(
                rawToken,
                "StrongPassword!42",
                "127.0.0.1",
                "JUnit"
        );

        assertEquals(AccountStatus.ACTIVE, invited.getAccountStatus());
        assertTrue(passwordEncoder.matches("StrongPassword!42", invited.getPasswordHash()));
        assertNotNull(invitation.getUsedAt());
        verify(auditLogService).logAdminAction(
                eq(invited.getId()),
                eq(RoleCode.COURSE_MANAGER.name()),
                eq("ACTIVATE_INTERNAL_ADMIN_ACCOUNT"),
                eq("INTERNAL_ADMIN_ACCOUNT"),
                eq(invited.getId()),
                eq(Map.of("status", AccountStatus.DISABLED.name())),
                eq(Map.of("status", AccountStatus.ACTIVE.name())),
                any()
        );

        BusinessException reused = assertThrows(
                BusinessException.class,
                () -> service.accept(
                        rawToken,
                        "AnotherStrong!42",
                        "127.0.0.1",
                        "JUnit"
                )
        );
        assertEquals(MessageCodes.INTERNAL_ADMIN_INVITATION_INVALID, reused.getMessageCode());
    }

    @Test
    void weakPasswordDoesNotReadOrConsumeInvitation() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.accept("token", "weak", null, null)
        );

        assertEquals(MessageCodes.INTERNAL_ADMIN_PASSWORD_INVALID, error.getMessageCode());
        verify(invitationRepository, never()).findByTokenHash(any());
        verify(invitationRepository, never()).findByTokenHashForUpdate(any());
    }

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

    private String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
