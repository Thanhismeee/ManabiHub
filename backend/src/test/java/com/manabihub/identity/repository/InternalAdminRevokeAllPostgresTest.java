package com.manabihub.identity.repository;

import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.identity.service.SecureTokenService;
import com.manabihub.identity.service.impl.InternalAdminSessionServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Runs revokeAll() against PostgreSQL so the effect on
 * internal_admin_refresh_tokens is observed, not mocked.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("it")
class InternalAdminRevokeAllPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private InternalAdminSessionRepository sessionRepository;
    @Autowired private InternalAdminRefreshTokenRepository refreshTokenRepository;
    @Autowired private InternalAdminAccountRepository accountRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void revokeAllAlsoRevokesActiveRefreshTokensOfEverySessionOfTheAccount() {
        UUID account = insertAccount("revoke-all@example.com");
        UUID bystander = insertAccount("bystander@example.com");
        UUID sessionA = insertSession(account);
        UUID sessionB = insertSession(account);
        UUID bystanderSession = insertSession(bystander);
        insertRefreshToken(sessionA, "ROTATED");
        insertRefreshToken(sessionA, "ACTIVE");
        insertRefreshToken(sessionB, "ACTIVE");
        insertRefreshToken(bystanderSession, "ACTIVE");

        service().revokeAll(account);

        assertThat(revokedSessionCount(account)).isEqualTo(2);
        assertThat(statuses(sessionA)).containsExactlyInAnyOrder("ROTATED", "REVOKED");
        assertThat(statuses(sessionB)).containsExactly("REVOKED");
        assertThat(revokedSessionCount(bystander)).isZero();
        assertThat(statuses(bystanderSession)).containsExactly("ACTIVE");
    }

    private InternalAdminSessionServiceImpl service() {
        return new InternalAdminSessionServiceImpl(
                sessionRepository,
                refreshTokenRepository,
                accountRepository,
                auditLogRepository,
                new SecureTokenService(),
                Mockito.mock(JwtEncoder.class)
        );
    }

    private UUID insertAccount(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO internal_admin_accounts (id, email, full_name, password_hash)
                VALUES (?, ?, 'Test Admin', 'not-a-real-hash')
                """, id, email);
        return id;
    }

    private UUID insertSession(UUID accountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO internal_admin_sessions
                    (id, admin_account_id, csrf_token_hash, credential_version,
                     expires_at, idle_expires_at)
                VALUES (?, ?, ?, 1, NOW() + INTERVAL '12 hours', NOW() + INTERVAL '12 hours')
                """, id, accountId, randomHash());
        return id;
    }

    private void insertRefreshToken(UUID sessionId, String status) {
        jdbc.update("""
                INSERT INTO internal_admin_refresh_tokens
                    (session_id, token_hash, status, expires_at)
                VALUES (?, ?, ?, NOW() + INTERVAL '12 hours')
                """, sessionId, randomHash(), status);
    }

    private List<String> statuses(UUID sessionId) {
        return jdbc.queryForList(
                "SELECT status FROM internal_admin_refresh_tokens WHERE session_id = ?",
                String.class, sessionId);
    }

    private int revokedSessionCount(UUID accountId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM internal_admin_sessions
                WHERE admin_account_id = ? AND revoked_at IS NOT NULL
                """, Integer.class, accountId);
        return count == null ? 0 : count;
    }

    private static String randomHash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }
}
