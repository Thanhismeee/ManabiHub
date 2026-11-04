package com.manabihub.identity.bootstrap;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.event.InternalAdminSessionsInvalidatedEvent;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionAdminBootstrapInitializerTest {

    private static final String STRONG_PASSWORD = "InitialAdmin#2026Secure";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private InternalAdminAccountRepository adminAccountRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ApplicationArguments applicationArguments;

    private Role systemAdminRole;

    @BeforeEach
    void setUp() {
        systemAdminRole = new Role();
        systemAdminRole.setId(UUID.randomUUID());
        systemAdminRole.setCode(RoleCode.SYSTEM_ADMIN);
        systemAdminRole.setName("System Admin");
    }

    @Test
    void run_WhenActiveSystemAdminExists_DoesNotReadOrResetBootstrapCredential() {
        InternalAdminAccount activeAdmin = new InternalAdminAccount();
        activeAdmin.setId(UUID.randomUUID());
        when(adminAccountRepository.findAllByStatusAndRoleCodeForUpdate(
                AccountStatus.ACTIVE,
                RoleCode.SYSTEM_ADMIN
        )).thenReturn(List.of(activeAdmin));

        initializer("", "", "").run(applicationArguments);

        verify(passwordEncoder, never()).encode(any());
        verify(adminAccountRepository, never()).saveAndFlush(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void run_WhenNoActiveSystemAdminAndCredentialMissing_FailsClosed() {
        when(adminAccountRepository.findAllByStatusAndRoleCodeForUpdate(
                AccountStatus.ACTIVE,
                RoleCode.SYSTEM_ADMIN
        )).thenReturn(List.of());

        assertThatThrownBy(() -> initializer("", "", "").run(applicationArguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_EMAIL");

        verify(adminAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void run_WhenNoActiveSystemAdmin_CreatesAuditedAccountWithoutCredentialLeakage() {
        UUID accountId = UUID.randomUUID();
        when(adminAccountRepository.findAllByStatusAndRoleCodeForUpdate(
                AccountStatus.ACTIVE,
                RoleCode.SYSTEM_ADMIN
        )).thenReturn(List.of());
        when(roleRepository.findByCode(RoleCode.SYSTEM_ADMIN)).thenReturn(Optional.of(systemAdminRole));
        when(adminAccountRepository.findByEmailIgnoreCaseForUpdate("admin@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("bcrypt-hash");
        when(adminAccountRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InternalAdminAccount account = invocation.getArgument(0);
            account.setId(accountId);
            return account;
        });

        initializer(
                " Admin@Example.com ",
                STRONG_PASSWORD,
                " Duc Long "
        ).run(applicationArguments);

        ArgumentCaptor<InternalAdminAccount> accountCaptor =
                ArgumentCaptor.forClass(InternalAdminAccount.class);
        verify(adminAccountRepository).saveAndFlush(accountCaptor.capture());
        InternalAdminAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getEmail()).isEqualTo("admin@example.com");
        assertThat(savedAccount.getFullName()).isEqualTo("Duc Long");
        assertThat(savedAccount.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(savedAccount.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(savedAccount.getRole()).isSameAs(systemAdminRole);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();
        assertThat(auditLog.getAction()).isEqualTo("BOOTSTRAP_SYSTEM_ADMIN");
        assertThat(auditLog.getTargetId()).isEqualTo(accountId);
        assertThat(auditLog.getAfterValue()).doesNotContainValue(STRONG_PASSWORD);
        assertThat(auditLog.getMetadata()).doesNotContainValue(STRONG_PASSWORD);
    }

    @Test
    void run_WhenBootstrapEmailAlreadyExists_ReactivatesItAsSystemAdmin() {
        InternalAdminAccount disabledAccount = new InternalAdminAccount();
        disabledAccount.setId(UUID.randomUUID());
        disabledAccount.setEmail("admin@example.com");
        disabledAccount.setAccountStatus(AccountStatus.DISABLED);
        disabledAccount.setCredentialVersion(4);

        when(adminAccountRepository.findAllByStatusAndRoleCodeForUpdate(
                AccountStatus.ACTIVE,
                RoleCode.SYSTEM_ADMIN
        )).thenReturn(List.of());
        when(roleRepository.findByCode(RoleCode.SYSTEM_ADMIN)).thenReturn(Optional.of(systemAdminRole));
        when(adminAccountRepository.findByEmailIgnoreCaseForUpdate("admin@example.com"))
                .thenReturn(Optional.of(disabledAccount));
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("new-hash");
        when(adminAccountRepository.saveAndFlush(disabledAccount)).thenReturn(disabledAccount);

        initializer("admin@example.com", STRONG_PASSWORD, "Duc Long")
                .run(applicationArguments);

        assertThat(disabledAccount.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(disabledAccount.getRole()).isSameAs(systemAdminRole);
        assertThat(disabledAccount.getPasswordHash()).isEqualTo("new-hash");
        assertThat(disabledAccount.getCredentialVersion()).isEqualTo(5);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOfSatisfying(
                        InternalAdminSessionsInvalidatedEvent.class,
                        invalidated -> {
                            assertThat(invalidated.adminAccountId())
                                    .isEqualTo(disabledAccount.getId());
                            assertThat(invalidated.reason())
                                    .isEqualTo("BOOTSTRAP_CREDENTIAL_REPLACED");
                        }
                );
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void run_WhenPasswordIsWeak_RejectsBootstrap() {
        when(adminAccountRepository.findAllByStatusAndRoleCodeForUpdate(
                AccountStatus.ACTIVE,
                RoleCode.SYSTEM_ADMIN
        )).thenReturn(List.of());

        assertThatThrownBy(() -> initializer(
                "admin@example.com",
                "weak-password",
                "Duc Long"
        ).run(applicationArguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
    }

    private ProductionAdminBootstrapInitializer initializer(
            String email,
            String password,
            String fullName
    ) {
        return new ProductionAdminBootstrapInitializer(
                jdbcTemplate,
                adminAccountRepository,
                roleRepository,
                passwordEncoder,
                auditLogRepository,
                eventPublisher,
                email,
                password,
                fullName
        );
    }
}
