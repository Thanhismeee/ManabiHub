package com.manabihub.refund;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AutomaticRefundMigrationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migrationRecoversOnlyUntouchedEligibleRequestsAndRequiresReconciliationReason() throws Exception {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE refund_requests (
                    id INTEGER PRIMARY KEY, status VARCHAR(30), decided_at TIMESTAMPTZ,
                    decided_by UUID, provider_status VARCHAR(40), eligibility_snapshot JSONB,
                    reconciliation_reason_code VARCHAR(80)
                )
                """);
        jdbc.execute("""
                INSERT INTO refund_requests (id, status, provider_status, eligibility_snapshot) VALUES
                (1, 'PENDING', 'NOT_REQUESTED', '{"refundType":"STANDARD","eligibilityResult":"STANDARD_ELIGIBLE","eligible":true}'),
                (2, 'PENDING', 'NOT_REQUESTED', '{"refundType":"DISPUTE","eligibilityResult":"MANUAL_REVIEW_REQUIRED","eligible":false}'),
                (3, 'RECONCILIATION_REQUIRED', 'NOT_REQUESTED', '{}'),
                (4, 'APPROVED', 'NOT_REQUESTED', '{"refundType":"STANDARD","eligibilityResult":"STANDARD_ELIGIBLE","eligible":true}'),
                (5, 'PENDING', 'NOT_REQUESTED', '{"refundType":"STANDARD","eligibilityResult":"STANDARD_ELIGIBLE","eligible":true}')
                """);
        jdbc.update("UPDATE refund_requests SET decided_at = CURRENT_TIMESTAMP WHERE id = 5");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V082__durable_automatic_wallet_refunds.sql"));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM refund_requests WHERE auto_refund_next_attempt_at IS NOT NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT id FROM refund_requests WHERE auto_refund_next_attempt_at IS NOT NULL", Integer.class));
        assertEquals("LEGACY_REASON_NOT_RECORDED", jdbc.queryForObject("SELECT reconciliation_reason_code FROM refund_requests WHERE id = 3", String.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE refund_requests SET status = 'RECONCILIATION_REQUIRED' WHERE id = 2"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE refund_requests SET reconciliation_reason_code = '  ' WHERE id = 3"));
    }
}
