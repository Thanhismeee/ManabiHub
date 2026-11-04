package com.manabihub.wallet.service.impl;

import com.manabihub.wallet.service.EscrowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class EscrowReleaseConcurrencyPostgresTest {

    private static final UUID STUDENT_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID TEACHER_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID =
            UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine");
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private EscrowService escrowService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentReleaseCreditsWalletAndLedgerExactlyOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID escrowId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID heldCommissionId = UUID.randomUUID();
        BigDecimal grossAmount = new BigDecimal("100000.00");
        BigDecimal amount = new BigDecimal("80000.00");
        BigDecimal commission = new BigDecimal("20000.00");

        jdbcTemplate.update("""
                INSERT INTO orders
                    (id, student_id, order_code, total_amount, currency, order_status)
                VALUES (?, ?, ?, ?, 'VND', 'PAID')
                """, orderId, STUDENT_ID, "MHB38-" + orderId, grossAmount);
        jdbcTemplate.update("""
                INSERT INTO order_items (id, order_id, course_id, price)
                VALUES (?, ?, ?, ?)
                """, orderItemId, orderId, COURSE_ID, grossAmount);
        jdbcTemplate.update("""
                INSERT INTO payment_transactions
                    (id, order_id, provider, provider_transaction_id, amount, status, succeeded_at)
                VALUES (?, ?, 'VNPAY', ?, ?, 'SUCCESS', ?)
                """, UUID.randomUUID(), orderId, "release-" + orderId, grossAmount,
                Timestamp.from(Instant.now().minusSeconds(30L * 86400)));
        jdbcTemplate.update("""
                INSERT INTO order_item_snapshots (
                    id,
                    order_item_id,
                    currency,
                    gross_amount,
                    commission_rate,
                    commission_amount,
                    teacher_net_amount,
                    commercial_policy_version,
                    escrow_days
                )
                VALUES (?, ?, 'VND', ?, 0.20, ?, ?, 'release-test', 14)
                """, snapshotId, orderItemId, grossAmount, commission, amount);
        jdbcTemplate.update("""
                INSERT INTO wallets
                    (id, owner_type, teacher_id, balance, frozen_balance, currency, frozen)
                VALUES (?, 'TEACHER', ?, ?, ?, 'VND', FALSE)
                """, walletId, TEACHER_ID, amount, amount);
        jdbcTemplate.update("""
                INSERT INTO escrow_ledger (
                    id,
                    order_id,
                    order_item_id,
                    course_id,
                    teacher_id,
                    amount,
                    status,
                    release_at,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, 'HELD', ?, ?)
                """,
                escrowId,
                orderId,
                orderItemId,
                COURSE_ID,
                TEACHER_ID,
                amount,
                Timestamp.from(Instant.now().minusSeconds(60)),
                Timestamp.from(Instant.now().minusSeconds(120)));
        jdbcTemplate.update("""
                INSERT INTO platform_commission_ledgers (
                    id, order_id, order_item_id, amount, event_type
                )
                VALUES (?, ?, ?, ?, 'COMMISSION_HELD')
                """, heldCommissionId, orderId, orderItemId, commission);

        int workers = 10;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return escrowService.processEscrowRelease(escrowId);
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Workers did not become ready");
            start.countDown();

            int successfulReleases = 0;
            List<Throwable> errors = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                try {
                    if (future.get(30, TimeUnit.SECONDS)) {
                        successfulReleases++;
                    }
                } catch (Exception exception) {
                    errors.add(exception.getCause() == null ? exception : exception.getCause());
                }
            }

            assertEquals(List.of(), errors, "Concurrent releases must not fail");
            assertEquals(1, successfulReleases, "Exactly one caller must release the escrow");
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor must terminate");
        }

        assertEquals("RELEASED", jdbcTemplate.queryForObject(
                "SELECT status FROM escrow_ledger WHERE id = ?",
                String.class,
                escrowId));
        assertEquals(0, amount.compareTo(jdbcTemplate.queryForObject(
                "SELECT balance FROM wallets WHERE id = ?",
                BigDecimal.class,
                walletId)));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbcTemplate.queryForObject(
                "SELECT frozen_balance FROM wallets WHERE id = ?",
                BigDecimal.class,
                walletId)));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM wallet_transactions
                WHERE transaction_type = 'ESCROW_RELEASE'
                  AND reference_id = ?
                """, Integer.class, escrowId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM audit_logs
                WHERE action = 'ESCROW_RELEASE'
                  AND target_id = ?
                """, Integer.class, escrowId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM platform_commission_ledgers
                WHERE order_item_id = ?
                  AND event_type = 'COMMISSION_RECOGNIZED'
                  AND amount = 20000.00
                """, Integer.class, orderItemId));
    }
}
