package com.manabihub.identity.service.impl;

import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.dto.request.LoginRequest;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.AdminLoginProtection;
import com.manabihub.identity.service.AdminSessionBundle;
import com.manabihub.identity.service.InternalAdminSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImplTest {

    @Mock private InternalAdminAccountRepository accountRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AdminLoginProtection loginProtection;
    @Mock private InternalAdminSessionService sessionService;
    @Mock private SecurityAuditService securityAuditService;

    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAuthServiceImpl(
                accountRepository,
                auditLogRepository,
                passwordEncoder,
                loginProtection,
                sessionService,
                securityAuditService
        );
    }

    @Test
    void unknownAccountPerformsDummyHashAndRecordsDurableFailure() {
        LoginRequest request = request(false);
        when(accountRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> service.login(request, "127.0.0.1", "JUnit")
        );

        verify(passwordEncoder).matches(
                eq("StrongPassword!42"),
                any(String.class)
        );
        verify(loginProtection).recordFailure(
                "admin@example.com",
                "127.0.0.1"
        );
    }

    @Test
    void disabledAccountStillRunsPasswordHashAndRecordsFailure() {
        LoginRequest request = request(false);
        InternalAdminAccount account = account();
        account.setAccountStatus(AccountStatus.DISABLED);
        when(accountRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches(
                "StrongPassword!42",
                account.getPasswordHash()
        )).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> service.login(request, "127.0.0.1", "JUnit")
        );

        verify(loginProtection).recordFailure(
                "admin@example.com",
                "127.0.0.1"
        );
        verify(securityAuditService).logInternalAdminAuthenticationFailure(
                account.getId(),
                RoleCode.SYSTEM_ADMIN.name(),
                "DISABLED_ACCOUNT",
                "127.0.0.1",
                "JUnit"
        );
    }

    @Test
    void successfulRememberedLoginCreatesServerSession() {
        LoginRequest request = request(true);
        InternalAdminAccount account = account();
        AdminSessionBundle expected = new AdminSessionBundle(
                "access",
                "refresh",
                "csrf",
                true,
                Instant.now().plusSeconds(600)
        );
        when(accountRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches(
                "StrongPassword!42",
                account.getPasswordHash()
        )).thenReturn(true);
        when(sessionService.create(account, true, "JUnit"))
                .thenReturn(expected);

        AdminSessionBundle actual = service.login(
                request,
                "127.0.0.1",
                "JUnit"
        );

        assertSame(expected, actual);
        verify(loginProtection).reset("admin@example.com", "127.0.0.1");
        verify(sessionService).create(account, true, "JUnit");
    }

    private LoginRequest request(boolean rememberMe) {
        LoginRequest request = new LoginRequest();
        request.setEmail(" Admin@Example.com ");
        request.setPassword("StrongPassword!42");
        request.setRememberMe(rememberMe);
        return request;
    }

    private InternalAdminAccount account() {
        Role role = new Role();
        role.setCode(RoleCode.SYSTEM_ADMIN);
        InternalAdminAccount account = new InternalAdminAccount();
        account.setId(UUID.randomUUID());
        account.setEmail("admin@example.com");
        account.setFullName("System Admin");
        account.setPasswordHash("stored-hash");
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setRole(role);
        return account;
    }
}
