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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@Profile("prod")
public class ProductionAdminBootstrapInitializer implements ApplicationRunner {

    private static final long BOOTSTRAP_LOCK_ID = 4_846_425_272_024L;
    private static final int MIN_PASSWORD_BYTES = 20;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final JdbcTemplate jdbcTemplate;
    private final InternalAdminAccountRepository adminAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final String bootstrapFullName;

    public ProductionAdminBootstrapInitializer(
            JdbcTemplate jdbcTemplate,
            InternalAdminAccountRepository adminAccountRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditLogRepository auditLogRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${manabihub.bootstrap.admin.email:}") String bootstrapEmail,
            @Value("${manabihub.bootstrap.admin.password:}") String bootstrapPassword,
            @Value("${manabihub.bootstrap.admin.full-name:ManabiHub System Administrator}")
            String bootstrapFullName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminAccountRepository = adminAccountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapFullName = bootstrapFullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + BOOTSTRAP_LOCK_ID + ")");

        List<InternalAdminAccount> activeSystemAdmins =
                adminAccountRepository.findAllByStatusAndRoleCodeForUpdate(
                        AccountStatus.ACTIVE,
                        RoleCode.SYSTEM_ADMIN
                );
        if (!activeSystemAdmins.isEmpty()) {
            log.info("Production SYSTEM_ADMIN bootstrap skipped because an active account already exists.");
            return;
        }

        BootstrapCredential credential = validateCredential();
        Role systemAdminRole = roleRepository.findByCode(RoleCode.SYSTEM_ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "SYSTEM_ADMIN role is missing; production admin bootstrap cannot continue."
                ));

        InternalAdminAccount account = adminAccountRepository
                .findByEmailIgnoreCaseForUpdate(credential.email())
                .orElseGet(InternalAdminAccount::new);
        boolean reactivatingExistingAccount = account.getId() != null;
        account.setEmail(credential.email());
        account.setFullName(credential.fullName());
        account.setPasswordHash(passwordEncoder.encode(credential.password()));
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setRole(systemAdminRole);
        if (reactivatingExistingAccount) {
            account.setCredentialVersion(Math.max(
                    1,
                    account.getCredentialVersion() + 1
            ));
        }
        InternalAdminAccount savedAccount = adminAccountRepository.saveAndFlush(account);
        if (reactivatingExistingAccount) {
            eventPublisher.publishEvent(new InternalAdminSessionsInvalidatedEvent(
                    savedAccount.getId(),
                    "BOOTSTRAP_CREDENTIAL_REPLACED",
                    Instant.now()
            ));
        }

        auditLogRepository.save(AuditLog.builder()
                .actorType("SYSTEM")
                .actorAdminId(savedAccount.getId())
                .actorRoleCode(RoleCode.SYSTEM_ADMIN.name())
                .action("BOOTSTRAP_SYSTEM_ADMIN")
                .targetType("INTERNAL_ADMIN_ACCOUNT")
                .targetId(savedAccount.getId())
                .afterValue(Map.of(
                        "status", AccountStatus.ACTIVE.name(),
                        "role", RoleCode.SYSTEM_ADMIN.name()
                ))
                .metadata(Map.of("source", "PRODUCTION_BOOTSTRAP"))
                .build());

        log.warn(
                "Bootstrapped the initial production SYSTEM_ADMIN account with id {}. "
                        + "Remove the bootstrap credential environment properties after login is verified.",
                savedAccount.getId()
        );
    }

    private BootstrapCredential validateCredential() {
        String normalizedEmail = bootstrapEmail == null
                ? ""
                : bootstrapEmail.trim().toLowerCase(Locale.ROOT);
        String normalizedFullName = bootstrapFullName == null ? "" : bootstrapFullName.trim();

        if (!StringUtils.hasText(normalizedEmail)
                || normalizedEmail.length() > 255
                || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || normalizedEmail.endsWith("@manabihub.local")) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_EMAIL must be a valid non-demo email when no active SYSTEM_ADMIN exists."
            );
        }
        if (!StringUtils.hasText(normalizedFullName) || normalizedFullName.length() > 255) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_FULL_NAME must contain between 1 and 255 characters."
            );
        }

        int passwordBytes = bootstrapPassword == null
                ? 0
                : bootstrapPassword.getBytes(StandardCharsets.UTF_8).length;
        boolean strongPassword = bootstrapPassword != null
                && bootstrapPassword.matches(".*[A-Z].*")
                && bootstrapPassword.matches(".*[a-z].*")
                && bootstrapPassword.matches(".*\\d.*")
                && bootstrapPassword.matches(".*[^A-Za-z0-9].*")
                && bootstrapPassword.chars().noneMatch(Character::isWhitespace);
        if (passwordBytes < MIN_PASSWORD_BYTES
                || passwordBytes > MAX_BCRYPT_PASSWORD_BYTES
                || !strongPassword) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_PASSWORD must be 20-72 UTF-8 bytes and contain uppercase, "
                            + "lowercase, digit, and special characters without whitespace."
            );
        }

        return new BootstrapCredential(normalizedEmail, bootstrapPassword, normalizedFullName);
    }

    private record BootstrapCredential(String email, String password, String fullName) {
    }
}
