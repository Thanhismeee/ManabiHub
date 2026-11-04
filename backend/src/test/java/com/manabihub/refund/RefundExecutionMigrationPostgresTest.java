package com.manabihub.refund;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RefundExecutionMigrationPostgresTest {

    private static final UUID STUDENT_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_ID =
            UUID.fromString("f0000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void refundMigrationsBackfillItemAndEnforceItemThenOrderScopedUniqueness() throws Exception {
        migrateTo("47");
        JdbcTemplate jdbc = jdbc();

        UUID legacyOrderId = UUID.randomUUID();
        UUID legacyItemId = UUID.randomUUID();
        insertOrderAndItem(jdbc, legacyOrderId, legacyItemId, "LEGACY-REFUND");
        jdbc.update("""
                INSERT INTO refund_requests (
                    id,
                    order_id,
                    student_id,
                    status,
                    reason
                )
                VALUES (?, ?, ?, 'PENDING', 'Legacy item-scoped refund')
                """, UUID.randomUUID(), legacyOrderId, STUDENT_ID);

        migrateTo("48");

        assertEquals(
                legacyItemId,
                jdbc.queryForObject("""
                        SELECT order_item_id
                        FROM refund_requests
                        WHERE order_id = ?
                        """, UUID.class, legacyOrderId)
        );

        UUID concurrentOrderId = UUID.randomUUID();
        UUID concurrentItemId = UUID.randomUUID();
        insertOrderAndItem(
                jdbc,
                concurrentOrderId,
                concurrentItemId,
                "CONCURRENT-REFUND"
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> insertActiveRefund(
                    concurrentOrderId,
                    concurrentItemId,
                    ready,
                    start
            ));
            Future<Boolean> second = executor.submit(() -> insertActiveRefund(
                    concurrentOrderId,
                    concurrentItemId,
                    ready,
                    start
            ));

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successes);
            assertEquals(
                    1,
                    jdbc.queryForObject("""
                            SELECT COUNT(*)
                            FROM refund_requests
                            WHERE order_item_id = ?
                              AND status IN (
                                  'PENDING',
                                  'PROCESSING',
                                  'RECONCILIATION_REQUIRED',
                                  'APPROVED'
                              )
                            """, Integer.class, concurrentItemId)
            );
        } finally {
            executor.shutdownNow();
        }

        assertEquals(
                2,
                jdbc.queryForObject("""
                        SELECT COUNT(DISTINCT permission.code)
                        FROM role_permissions mapping
                        JOIN roles role ON role.id = mapping.role_id
                        JOIN permissions permission
                          ON permission.id = mapping.permission_id
                        WHERE role.code = 'SYSTEM_ADMIN'
                          AND permission.code IN (
                              'REFUND_REVIEW',
                              'FINANCE_EVIDENCE_VIEW'
                          )
                        """, Integer.class)
        );
        assertEquals(
                0,
                jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM role_permissions mapping
                        JOIN roles role ON role.id = mapping.role_id
                        JOIN permissions permission
                          ON permission.id = mapping.permission_id
                        WHERE role.code = 'COURSE_MANAGER'
                          AND permission.code IN (
                              'REFUND_REVIEW',
                              'FINANCE_EVIDENCE_VIEW'
                          )
                        """, Integer.class)
        );

        migrateTo("55");

        assertEquals("14", jdbc.queryForObject("""
                SELECT setting_value
                FROM system_settings
                WHERE setting_key = 'REFUND_WINDOW_DAYS'
                """, String.class));
        assertEquals("20", jdbc.queryForObject("""
                SELECT setting_value
                FROM system_settings
                WHERE setting_key = 'REFUND_PROGRESS_LIMIT_PERCENT'
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_name = 'enrollments'
                  AND column_name = 'protected_materials_fully_downloaded_at'
                """, Integer.class));

        UUID secondItemId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO order_items (id, order_id, course_id, price)
                VALUES (?, ?, ?, 250000.00)
                """, secondItemId, concurrentOrderId, COURSE_ID);

        assertThrows(RuntimeException.class, () -> jdbc.update("""
                INSERT INTO refund_requests (
                    id,
                    order_id,
                    order_item_id,
                    student_id,
                    status,
                    reason
                )
                VALUES (?, ?, ?, ?, 'PENDING', 'Duplicate active order refund')
                """, UUID.randomUUID(), concurrentOrderId, secondItemId, STUDENT_ID));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM refund_requests
                WHERE order_id = ?
                  AND status IN (
                      'PENDING',
                      'PROCESSING',
                      'RECONCILIATION_REQUIRED',
                      'APPROVED'
                  )
                """, Integer.class, concurrentOrderId));
    }

    private boolean insertActiveRefund(
            UUID orderId,
            UUID itemId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return false;
            }
            return jdbc().update("""
                    INSERT INTO refund_requests (
                        id,
                        order_id,
                        order_item_id,
                        student_id,
                        status,
                        reason
                    )
                    VALUES (?, ?, ?, ?, 'PENDING', 'Concurrent refund')
                    """, UUID.randomUUID(), orderId, itemId, STUDENT_ID) == 1;
        } catch (RuntimeException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void insertOrderAndItem(
            JdbcTemplate jdbc,
            UUID orderId,
            UUID itemId,
            String orderCode
    ) {
        jdbc.update("""
                INSERT INTO orders (
                    id,
                    student_id,
                    order_code,
                    total_amount,
                    currency,
                    order_status
                )
                VALUES (?, ?, ?, 250000.00, 'VND', 'PENDING')
                """,
                orderId,
                STUDENT_ID,
                orderCode + "-" + orderId.toString().substring(0, 8));
        jdbc.update("""
                INSERT INTO order_items (
                    id,
                    order_id,
                    course_id,
                    price
                )
                VALUES (?, ?, ?, 250000.00)
                """, itemId, orderId, COURSE_ID);
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ));
    }
}
