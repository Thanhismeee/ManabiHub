package com.manabihub.systemconfig;

import com.manabihub.identity.enums.RoleCode;
import com.manabihub.systemconfig.service.SystemAdministrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class SystemAdministrationPostgresIntegrationTest {

    private static final UUID SYSTEM_ADMIN_ID =
            UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_MANAGER_ID =
            UUID.fromString("c0000000-0000-0000-0000-000000000002");
    private static PostgreSQLContainer<?> postgresContainer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine");
        postgresContainer.start();
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired private SystemAdministrationService administrationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void versionedMigrationUpdatesSettingsAndMaintainsExactlyOneRoleWithAudit() {
        Integer disabledDemoAccounts = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM internal_admin_accounts
                WHERE email IN (
                    'sysadmin@manabihub.local',
                    'course.manager@manabihub.local',
                    'finance.manager@manabihub.local'
                )
                  AND account_status = 'DISABLED'
                """,
                Integer.class
        );
        assertEquals(3, disabledDemoAccounts);

        jdbcTemplate.update(
                """
                UPDATE internal_admin_accounts
                SET account_status = 'ACTIVE'
                WHERE id IN (?, ?)
                """,
                SYSTEM_ADMIN_ID,
                COURSE_MANAGER_ID
        );

        Integer auditCountBefore = countConfigurationAudits();
        var settings = administrationService.listSettings(SYSTEM_ADMIN_ID);
        assertEquals(20, settings.size());
        Set<String> settingKeys = settings.stream()
                .map(setting -> setting.key())
                .collect(Collectors.toSet());
        assertTrue(settingKeys.containsAll(Set.of(
                "ADMIN_LOCKOUT_MAX_ATTEMPTS",
                "CURRENCY",
                "WITHDRAWAL_FEE",
                "KYC_TARGET_DAYS_MIN",
                "KYC_TARGET_DAYS_MAX",
                "POLICY_VERSION",
                "POLICY_EFFECTIVE_AT"
        )));

        var updatedSetting = administrationService.updateSetting(
                SYSTEM_ADMIN_ID,
                "COMMISSION_RATE",
                "0.25",
                "Integration verification"
        );
        assertEquals("0.25", updatedSetting.value());

        var updatedAccount = administrationService.updateInternalAdminRole(
                SYSTEM_ADMIN_ID,
                COURSE_MANAGER_ID,
                RoleCode.FINANCE_MANAGER,
                "Integration verification"
        );
        assertEquals(RoleCode.FINANCE_MANAGER, updatedAccount.role());

        Integer assignmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM internal_admin_roles WHERE admin_account_id = ?",
                Integer.class,
                COURSE_MANAGER_ID
        );
        String liveRole = jdbcTemplate.queryForObject(
                """
                SELECT roles.code
                FROM internal_admin_roles assignments
                JOIN roles ON roles.id = assignments.role_id
                WHERE assignments.admin_account_id = ?
                """,
                String.class,
                COURSE_MANAGER_ID
        );
        Integer auditCountAfter = countConfigurationAudits();

        assertEquals(1, assignmentCount);
        assertEquals("FINANCE_MANAGER", liveRole);
        assertEquals(2, auditCountAfter - auditCountBefore);
    }

    private Integer countConfigurationAudits() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE actor_admin_id = ?
                  AND action IN ('UPDATE_SYSTEM_SETTING', 'ASSIGN_INTERNAL_ROLE')
                """,
                Integer.class,
                SYSTEM_ADMIN_ID
        );
    }
}
