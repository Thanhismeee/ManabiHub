package com.manabihub.wallet.service.impl;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class FinancialIntegrityMigrationPostgresTest {

    private static final UUID STUDENT_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID TEACHER_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID =
            UUID.fromString("f0000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void v44BackfillsHeldEscrowIntoTotalAndEnforcesWalletInvariant() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("43"))
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));

        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID escrowId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO orders (
                    id, student_id, order_code, total_amount, currency, order_status
                )
                VALUES (?, ?, ?, 80000, 'VND', 'PAID')
                """, orderId, STUDENT_ID, "MIG44-" + orderId);
        jdbc.update("""
                INSERT INTO order_items (id, order_id, course_id, price)
                VALUES (?, ?, ?, 80000)
                """, orderItemId, orderId, COURSE_ID);
        jdbc.update("""
                INSERT INTO wallets (
                    id, owner_type, teacher_id, balance, frozen_balance, currency, frozen
                )
                VALUES (?, 'TEACHER', ?, 50000, 100000, 'VND', FALSE)
                """, walletId, TEACHER_ID);
        jdbc.update("""
                INSERT INTO escrow_ledger (
                    id, order_id, course_id, teacher_id, amount, status, release_at, created_at
                )
                VALUES (?, ?, ?, ?, 80000, 'HELD', ?, ?)
                """,
                escrowId,
                orderId,
                COURSE_ID,
                TEACHER_ID,
                Timestamp.from(Instant.now().plusSeconds(86400)),
                Timestamp.from(Instant.now().minusSeconds(60)));

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("44"))
                .load()
                .migrate();

        assertEquals(0, new BigDecimal("130000.00").compareTo(jdbc.queryForObject(
                "SELECT balance FROM wallets WHERE id = ?",
                BigDecimal.class,
                walletId)));
        assertEquals(0, new BigDecimal("100000.00").compareTo(jdbc.queryForObject(
                "SELECT frozen_balance FROM wallets WHERE id = ?",
                BigDecimal.class,
                walletId)));
        assertEquals(0, new BigDecimal("30000.00").compareTo(jdbc.queryForObject(
                "SELECT balance - frozen_balance FROM wallets WHERE id = ?",
                BigDecimal.class,
                walletId)));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM order_item_snapshots
                WHERE order_item_id = ?
                  AND commission_rate = 0
                  AND teacher_net_amount = 80000
                  AND commercial_policy_version = 'legacy-pre-v044'
                """, Integer.class, orderItemId));
        assertEquals(orderItemId, jdbc.queryForObject(
                "SELECT order_item_id FROM escrow_ledger WHERE id = ?",
                UUID.class,
                escrowId));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                UPDATE wallets
                SET balance = 1, frozen_balance = 2
                WHERE id = ?
                """, walletId));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                UPDATE escrow_ledger
                SET amount = 1
                WHERE id = ?
                """, escrowId));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO escrow_ledger (
                    id, order_id, course_id, teacher_id, amount, status
                )
                VALUES (?, ?, ?, ?, 80000, 'HELD')
                """,
                UUID.randomUUID(),
                orderId,
                COURSE_ID,
                TEACHER_ID));
    }

    @Test
    void v44FailsClosedWhenLegacyPaidItemHasNoEscrowAllocation() {
        String schema = "m44_bad_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .target(MigrationVersion.fromVersion("43"))
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO %s.orders (
                    id, student_id, order_code, total_amount, currency, order_status
                )
                VALUES (?, ?, ?, 80000, 'VND', 'PAID')
                """.formatted(schema), orderId, STUDENT_ID, "MIG44-BAD-" + orderId);
        jdbc.update("""
                INSERT INTO %s.order_items (id, order_id, course_id, price)
                VALUES (?, ?, ?, 80000)
                """.formatted(schema), UUID.randomUUID(), orderId, COURSE_ID);

        Flyway migration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("44"))
                .load();

        assertThrows(FlywayException.class, migration::migrate);
    }
}
