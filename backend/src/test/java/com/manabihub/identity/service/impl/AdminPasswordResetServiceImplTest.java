package com.manabihub.identity.service.impl;

import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.InternalAdminPasswordReset;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.event.InternalAdminPasswordChangedEvent;
import com.manabihub.identity.event.InternalAdminPasswordResetIssuedEvent;
import com.manabihub.identity.event.InternalAdminSessionsInvalidatedEvent;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.InternalAdminPasswordResetRepository;
import com.manabihub.identity.service.AdminLoginProtection;
import com.manabihub.identity.service.AdminPasswordChangeProtection;
import com.manabihub.identity.service.AdminPasswordResetRequestDispatcher;
import com.manabihub.identity.service.AdminPasswordResetRequestLimiter;
import com.manabihub.identity.service.InternalAdminPasswordPolicy;
import com.manabihub.identity.service.SecureTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPasswordResetServiceImplTest {

    @Mock private InternalAdminAccountRepository accountRepository;
    @Mock private InternalAdminPasswordResetRepository passwordResetRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private InternalAdminPasswordPolicy passwordPolicy;
    @Mock private SecureTokenService tokenService;
    @Mock private AdminPasswordResetRequestLimiter requestLimiter;
    @Mock private AdminPasswordResetRequestDispatcher requestDispatcher;
    @Mock private AdminLoginProtection loginProtection;
    @Mock private AdminPasswordChangeProtection changeProtection;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AdminPasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminPasswordResetServiceImpl(
                accountRepository,
                passwordResetRepository,
                auditLogRepository,
                passwordEncoder,
                passwordPolicy,
                tokenService,
                requestLimiter,
                requestDispatcher,
                loginProtection,
                changeProtection,
                eventPublisher
        );
        ReflectionTestUtils.setField(service, "resetTtlMinutes", 30L);
        ReflectionTestUtils.setField(service, "resetCooldownSeconds", 60L);
        lenient().when(requestDispatcher.dispatch(any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return true;
                });
    }

    @Test
    void unknownEmailReturnsSilentlyWithoutPublishingAReset() {
        when(requestLimiter.allow("unknown@example.com", "127.0.0.1"))
                .thenReturn(true);
        when(accountRepository.findByEmailIgnoreCaseForUpdate("unknown@example.com"))
                .thenReturn(Optional.empty());

        service.request("unknown@example.com", "127.0.0.1", "JUnit");

        verifyNoInteractions(passwordResetRepository, eventPublisher);
    }

    @Test
    void rateLimitedRequestIsRejectedBeforeAsyncDispatch() {
        when(requestLimiter.allow("blocked@example.com", "127.0.0.1"))
                .thenReturn(false);

        service.request("blocked@example.com", "127.0.0.1", "JUnit");

        verify(requestDispatcher, never()).dispatch(any(Runnable.class));
        verifyNoInteractions(accountRepository, passwordResetRepository, eventPublisher);
    }

    @Test
    void admissionFailureIsFailClosedAndKeepsGenericControllerContract() {
        when(requestLimiter.allow("admin@example.com", "127.0.0.1"))
                .thenThrow(new IllegalStateException("database unavailable"));

        service.request("admin@example.com", "127.0.0.1", "JUnit");

        verify(requestDispatcher, never()).dispatch(any(Runnable.class));
        verifyNoInteractions(accountRepository, passwordResetRepository, eventPublisher);
    }

    @Test
    void requestStoresOnlyHashAndLeavesCurrentPasswordAndSessionsUntouched() {
        InternalAdminAccount account = account();
        when(requestLimiter.allow(account.getEmail(), "127.0.0.1"))
                .thenReturn(true);
        when(accountRepository.findByEmailIgnoreCaseForUpdate(account.getEmail()))
                .thenReturn(Optional.of(account));
        when(passwordResetRepository.findOpenForAccount(account.getId()))
                .thenReturn(Optional.empty());
        when(tokenService.randomToken()).thenReturn("raw-reset-token");
        when(tokenService.hash("raw-reset-token")).thenReturn("reset-hash");

        service.request(account.getEmail(), "127.0.0.1", "JUnit");

        verify(passwordResetRepository).saveAndFlush(argThat(reset ->
                reset.getAdminAccountId().equals(account.getId())
                        && "reset-hash".equals(reset.getTokenHash())
                        && reset.getExpiresAt().isAfter(Instant.now())
        ));
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        InternalAdminPasswordResetIssuedEvent issued = assertInstanceOf(
                InternalAdminPasswordResetIssuedEvent.class,
                eventCaptor.getValue()
        );
        assertEquals("raw-reset-token", issued.rawToken());
        assertEquals(account.getId(), issued.adminAccountId());
        assertEquals("old-password-hash", account.getPasswordHash());
    }

    @Test
    void successfulResetChangesPasswordAndInvalidatesEverySessionAfterCommit() {
        InternalAdminAccount account = account();
        InternalAdminPasswordReset reset = new InternalAdminPasswordReset();
        reset.setAdminAccountId(account.getId());
        reset.setTokenHash("reset-hash");
        reset.setExpiresAt(Instant.now().plusSeconds(600));
        when(tokenService.hash("raw-reset-token")).thenReturn("reset-hash");
        when(passwordResetRepository.findByTokenHash("reset-hash"))
                .thenReturn(Optional.of(reset));
        when(passwordResetRepository.findByTokenHashForUpdate("reset-hash"))
                .thenReturn(Optional.of(reset));
        when(accountRepository.findByIdForRoleUpdate(account.getId()))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("NewPassword!42", "old-password-hash"))
                .thenReturn(false);
        when(passwordEncoder.encode("NewPassword!42"))
                .thenReturn("new-password-hash");

        service.reset(
                "raw-reset-token",
                "NewPassword!42",
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("new-password-hash", account.getPasswordHash());
        assertEquals(3, account.getCredentialVersion());
        assertNotNull(reset.getUsedAt());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        InternalAdminPasswordChangedEvent changed = eventCaptor.getAllValues()
                .stream()
                .filter(InternalAdminPasswordChangedEvent.class::isInstance)
                .map(InternalAdminPasswordChangedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(account.getId(), changed.adminAccountId());
        InternalAdminSessionsInvalidatedEvent invalidated = eventCaptor.getAllValues()
                .stream()
                .filter(InternalAdminSessionsInvalidatedEvent.class::isInstance)
                .map(InternalAdminSessionsInvalidatedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(account.getId(), invalidated.adminAccountId());
        assertEquals("PASSWORD_RESET", invalidated.reason());
    }

    @Test
    void usedResetTokenCannotBeReplayed() {
        InternalAdminPasswordReset reset = new InternalAdminPasswordReset();
        reset.setUsedAt(Instant.now());
        reset.setExpiresAt(Instant.now().plusSeconds(600));
        when(tokenService.hash("used-token")).thenReturn("used-hash");
        when(passwordResetRepository.findByTokenHash("used-hash"))
                .thenReturn(Optional.of(reset));

        assertThrows(
                BusinessException.class,
                () -> service.reset(
                        "used-token",
                        "NewPassword!42",
                        "127.0.0.1",
                        "JUnit"
                )
        );

        verifyNoInteractions(accountRepository);
    }

    @Test
    void invalidCurrentPasswordIsRateLimitedAndAudited() {
        InternalAdminAccount account = account();
        when(accountRepository.findByIdForRoleUpdate(account.getId()))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "old-password-hash"))
                .thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.change(
                        account.getId(),
                        "wrong-password",
                        "NewPassword!42",
                        "127.0.0.1",
                        "JUnit"
                )
        );

        verify(changeProtection).check(account.getId(), "127.0.0.1");
        verify(changeProtection).recordFailure(account.getId(), "127.0.0.1");
        verify(auditLogRepository).save(argThat(audit ->
                "ADMIN_PASSWORD_CHANGE_FAILED".equals(audit.getAction())
                        && account.getId().equals(audit.getActorAdminId())
                        && "INVALID_CURRENT_PASSWORD".equals(
                                audit.getMetadata().get("reason")
                        )
        ));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private InternalAdminAccount account() {
        Role role = new Role();
        role.setCode(RoleCode.FINANCE_MANAGER);
        InternalAdminAccount account = new InternalAdminAccount();
        account.setId(UUID.randomUUID());
        account.setEmail("finance@example.com");
        account.setFullName("Finance Manager");
        account.setPasswordHash("old-password-hash");
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setCredentialVersion(2);
        account.setRole(role);
        return account;
    }
}
