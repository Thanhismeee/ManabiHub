package com.manabihub.common.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Restores deterministic demo logins only for the local development profile.
 *
 * <p>Flyway disables the baseline demo accounts for every environment. Keeping
 * this behavior in a local-only component prevents a known demo password from
 * being usable when the same migration set is deployed to production.</p>
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDemoAdminInitializer implements ApplicationRunner {

    private static final List<String> DEMO_ADMIN_EMAILS = List.of(
            "sysadmin@manabihub.local",
            "course.manager@manabihub.local",
            "finance.manager@manabihub.local"
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${manabihub.demo.admin-password:Admin@123}")
    private String demoAdminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String passwordHash = passwordEncoder.encode(demoAdminPassword);
        int updatedAccounts = 0;
        for (String email : DEMO_ADMIN_EMAILS) {
            updatedAccounts += jdbcTemplate.update(
                    """
                    UPDATE internal_admin_accounts
                    SET password_hash = ?,
                        account_status = 'ACTIVE',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE email = ?
                    """,
                    passwordHash,
                    email
            );
        }
        log.info(
                "Enabled {} local-only demo admin account(s); credential value was not logged.",
                updatedAccounts
        );
    }
}
