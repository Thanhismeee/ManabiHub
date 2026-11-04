package com.manabihub.payment.service.impl;

import com.manabihub.common.mail.EmailService;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.order.entity.Order;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.payment.dto.IpnAckResponse;
import com.manabihub.payment.gateway.PaymentCallbackResult;
import com.manabihub.payment.gateway.PaymentGateway;
import com.manabihub.payment.service.PaymentService;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.service.StudentWalletService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PaymentFinancialIntegrityConcurrencyPostgresTest {

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
    private PaymentService paymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudentWalletService studentWalletService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private PaymentGateway paymentGateway;

    @MockBean
    private EmailService emailService;

    @Test
    void creatingFirstStudentWalletKeepsSettlementPersistenceContextManaged() {
        String email = UUID.randomUUID() + "@refund.test";
        AtomicReference<UUID> createdStudentId = new AtomicReference<>();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            AppUser user = AppUser.builder()
                    .email(email)
                    .fullName("First wallet refund")
                    .build();
            entityManager.persist(user);
            StudentProfile student = StudentProfile.builder()
                    .user(user)
                    .build();
            entityManager.persist(student);
            entityManager.flush();
            createdStudentId.set(student.getId());

            assertTrue(entityManager.contains(student));
            studentWalletService.getOrCreateStudentWallet(student.getId());

            assertTrue(entityManager.contains(student),
                    "Creating a wallet must not detach the active refund settlement graph");
            assertEquals(email, student.getUser().getEmail());
        });

        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM wallets
                WHERE owner_type = ? AND student_id = ?
                """, Integer.class, WalletOwnerType.STUDENT.name(), createdStudentId.get()));
    }

    @Test
    void walletPaymentCommitsDebitEnrollmentEscrowAndNotificationEvent() {
        UUID courseId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        String orderCode = "WALLET-" + orderId;
        BigDecimal gross = new BigDecimal("250000.00");

        jdbcTemplate.update("""
                INSERT INTO courses (
                    id, teacher_id, title, slug, price, currency, status, ai_supported, published_at
                )
                VALUES (?, ?, 'Wallet integration course', ?, ?, 'VND', 'PUBLISHED', FALSE, NOW())
                """, courseId, TEACHER_ID, "wallet-integration-" + courseId, gross);
        jdbcTemplate.update("""
                INSERT INTO wallets (
                    id, owner_type, student_id, balance, frozen_balance, currency, frozen
                )
                VALUES (?, 'STUDENT', ?, 300000.00, 0, 'VND', FALSE)
                """, walletId, STUDENT_ID);
        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, student_id, order_code, total_amount, currency, order_status
                )
                VALUES (?, ?, ?, ?, 'VND', 'PENDING')
                """, orderId, STUDENT_ID, orderCode, gross);
        jdbcTemplate.update("""
                INSERT INTO order_items (id, order_id, course_id, price)
                VALUES (?, ?, ?, ?)
                """, orderItemId, orderId, courseId, gross);

        paymentService.payWithWallet(orderId);

        assertEquals("PAID", jdbcTemplate.queryForObject(
                "SELECT order_status FROM orders WHERE id = ?",
                String.class,
                orderId));
        assertEquals(0, new BigDecimal("50000.00").compareTo(jdbcTemplate.queryForObject(
                "SELECT balance FROM wallets WHERE id = ?",
                BigDecimal.class,
                walletId)));
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM payment_transactions
                WHERE order_id = ?
                  AND provider = 'WALLET'
                  AND status = 'SUCCESS'
                  AND succeeded_at IS NOT NULL
                """, orderId));
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM wallet_payment_reservations
                WHERE order_id = ?
                  AND status = 'CAPTURED'
                """, orderId));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbcTemplate.queryForObject(
                "SELECT frozen_balance FROM wallets WHERE id = ?",
                BigDecimal.class,
                walletId)));
        assertEquals(1, count(
                "SELECT COUNT(*) FROM enrollments WHERE student_id = ? AND course_id = ?",
                STUDENT_ID,
                courseId));
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM order_item_snapshots
                WHERE order_item_id = ?
                  AND commission_amount = 50000.00
                  AND teacher_net_amount = 200000.00
                """, orderItemId));
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM escrow_ledger
                WHERE order_item_id = ?
                  AND amount = 200000.00
                  AND status = 'HELD'
                """, orderItemId));
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM notifications
                WHERE dedupe_key = ?
                  AND recipient_user_id = 'd0000000-0000-0000-0000-000000000001'
                  AND notification_type = 'PURCHASE_SUCCESS'
                  AND title = 'Mua khoá học thành công'
                """, "payment:" + orderId
                        + ":d0000000-0000-0000-0000-000000000001:PURCHASE_SUCCESS"));
    }

    @Test
    void concurrentSuccessfulCallbacksCreateOneImmutableAllocationSet() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        String orderCode = "MHB74-" + orderId;
        BigDecimal gross = new BigDecimal("100000.00");
        BigDecimal balanceBefore = teacherBalance();
        BigDecimal frozenBefore = teacherFrozenBalance();
        BigDecimal availableBefore = balanceBefore.subtract(frozenBefore);

        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, student_id, order_code, total_amount, currency, order_status
                )
                VALUES (?, ?, ?, ?, 'VND', 'PENDING')
                """, orderId, STUDENT_ID, orderCode, gross);
        jdbcTemplate.update("""
                INSERT INTO order_items (id, order_id, course_id, price)
                VALUES (?, ?, ?, ?)
                """, orderItemId, orderId, COURSE_ID, gross);

        when(paymentGateway.getProvider()).thenReturn("TEST");
        when(paymentGateway.parseCallback(anyMap())).thenReturn(new PaymentCallbackResult(
                true,
                orderCode,
                "TX-" + orderId,
                10_000_000L,
                "00",
                "00",
                true));

        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IpnAckResponse>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return paymentService.handleIpn(Map.of("vnp_TxnRef", orderCode));
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Workers did not become ready");
            start.countDown();

            int confirmed = 0;
            int duplicate = 0;
            List<Throwable> errors = new ArrayList<>();
            for (Future<IpnAckResponse> future : futures) {
                try {
                    String code = future.get(30, TimeUnit.SECONDS).rspCode();
                    if ("00".equals(code)) {
                        confirmed++;
                    } else if ("02".equals(code)) {
                        duplicate++;
                    }
                } catch (Exception exception) {
                    errors.add(exception.getCause() == null ? exception : exception.getCause());
                }
            }

            assertEquals(List.of(), errors, "Concurrent callbacks must not fail");
            assertEquals(1, confirmed, "Exactly one callback must confirm payment");
            assertEquals(workers - 1, duplicate, "All other callbacks must be idempotent");
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor must terminate");
        }

        assertEquals("PAID", jdbcTemplate.queryForObject(
                "SELECT order_status FROM orders WHERE id = ?",
                String.class,
                orderId));
        assertEquals(1, count("SELECT COUNT(*) FROM order_item_snapshots WHERE order_item_id = ?", orderItemId));
        assertEquals(1, count("SELECT COUNT(*) FROM escrow_ledger WHERE order_item_id = ?", orderItemId));
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM platform_commission_ledgers
                WHERE order_item_id = ?
                  AND event_type = 'COMMISSION_HELD'
                  AND amount = 20000.00
                """, orderItemId));
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM wallet_transactions wallet_tx
                JOIN escrow_ledger escrow ON escrow.id = wallet_tx.reference_id
                WHERE escrow.order_item_id = ?
                  AND wallet_tx.reference_type = 'ESCROW'
                  AND wallet_tx.transaction_type = 'ESCROW_HOLD'
                  AND wallet_tx.amount = 80000.00
                """, orderItemId));
        assertEquals(0, frozenBefore.add(new BigDecimal("80000.00"))
                .compareTo(teacherFrozenBalance()));
        assertEquals(0, balanceBefore.add(new BigDecimal("80000.00"))
                .compareTo(teacherBalance()));
        assertEquals(0, availableBefore.compareTo(
                teacherBalance().subtract(teacherFrozenBalance())));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE order_item_snapshots
                SET commission_rate = 0.50
                WHERE order_item_id = ?
                """, orderItemId));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE platform_commission_ledgers
                SET amount = 1
                WHERE order_item_id = ?
                """, orderItemId));
    }

    @Test
    void combinedPaymentReservationPreventsWalletDoubleSpendBeforeGatewayCallback() {
        UUID studentId = createStudentProfile("combined-race");
        UUID walletId = UUID.randomUUID();
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO wallets (
                    id, owner_type, student_id, balance, frozen_balance, currency, frozen
                ) VALUES (?, 'STUDENT', ?, 100000.00, 0, 'VND', FALSE)
                """, walletId, studentId);
        insertPendingOrder(firstOrderId, studentId, "COMBINED-" + firstOrderId,
                new BigDecimal("250000.00"));
        insertPendingOrder(secondOrderId, studentId, "WALLET-" + secondOrderId,
                new BigDecimal("100000.00"));

        StudentProfile student = StudentProfile.builder().id(studentId).build();
        Order firstOrder = Order.builder()
                .id(firstOrderId)
                .student(student)
                .orderCode("COMBINED-" + firstOrderId)
                .totalAmount(new BigDecimal("250000.00"))
                .currency("VND")
                .status(OrderStatus.PENDING)
                .build();
        when(paymentGateway.getProvider()).thenReturn("TEST");
        when(paymentGateway.buildPaymentUrl(org.mockito.ArgumentMatchers.any(Order.class),
                org.mockito.ArgumentMatchers.anyString())).thenReturn("https://gateway.test/pay");

        assertEquals("https://gateway.test/pay",
                paymentService.initiateCombinedPayment(firstOrder, "127.0.0.1"));

        assertEquals(0, new BigDecimal("100000.00").compareTo(jdbcTemplate.queryForObject(
                "SELECT frozen_balance FROM wallets WHERE id = ?", BigDecimal.class, walletId)));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM wallet_payment_reservations
                WHERE order_id = ? AND amount = 100000.00 AND status = 'RESERVED'
                """, firstOrderId));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM payment_transactions
                WHERE order_id = ? AND status = 'PENDING'
                """, firstOrderId));
        assertThrows(BusinessException.class,
                () -> paymentService.payWithWallet(secondOrderId));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT order_status FROM orders WHERE id = ?", String.class, secondOrderId));

        UUID orderItemId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO order_items (id, order_id, course_id, price)
                VALUES (?, ?, ?, 250000.00)
                """, orderItemId, firstOrderId, COURSE_ID);
        when(paymentGateway.parseCallback(anyMap())).thenReturn(new PaymentCallbackResult(
                true,
                firstOrder.getOrderCode(),
                "COMBINED-TX-" + firstOrderId,
                15_000_000L,
                "00",
                "00",
                true));

        assertEquals("00", paymentService.handleIpn(
                Map.of("vnp_TxnRef", firstOrder.getOrderCode())).rspCode());
        assertEquals("PAID", jdbcTemplate.queryForObject(
                "SELECT order_status FROM orders WHERE id = ?", String.class, firstOrderId));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM payment_transactions
                WHERE order_id = ? AND status = 'SUCCESS' AND succeeded_at IS NOT NULL
                """, firstOrderId));
        assertEquals(0, new BigDecimal("250000.00").compareTo(jdbcTemplate.queryForObject("""
                SELECT SUM(amount) FROM payment_transactions
                WHERE order_id = ? AND status = 'SUCCESS'
                """, BigDecimal.class, firstOrderId)));
    }

    @Test
    void concurrentFirstWalletCreationCreatesExactlyOneCanonicalWallet() throws Exception {
        UUID studentId = createStudentProfile("wallet-create-race");
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return studentWalletService.getOrCreateStudentWallet(studentId).getId();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<UUID> walletIds = new ArrayList<>();
            for (Future<UUID> future : futures) {
                walletIds.add(future.get(30, TimeUnit.SECONDS));
            }
            assertEquals(1, walletIds.stream().distinct().count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(1, count("""
                SELECT COUNT(*) FROM wallets
                WHERE owner_type = 'STUDENT' AND student_id = ?
                """, studentId));
    }

    @Test
    void concurrentRefundCreditReturnsFullAmountToWalletExactlyOnce() throws Exception {
        UUID studentId = createStudentProfile("refund-wallet-race");
        UUID refundRequestId = UUID.randomUUID();
        BigDecimal refundAmount = new BigDecimal("250000.00");
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return studentWalletService.creditRefund(
                            studentId,
                            refundAmount,
                            refundRequestId,
                            "Refund concurrency test").getId();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<UUID> ledgerIds = new ArrayList<>();
            for (Future<UUID> future : futures) {
                ledgerIds.add(future.get(30, TimeUnit.SECONDS));
            }
            assertEquals(1, ledgerIds.stream().distinct().count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(0, refundAmount.compareTo(jdbcTemplate.queryForObject("""
                SELECT balance FROM wallets
                WHERE owner_type = 'STUDENT' AND student_id = ?
                """, BigDecimal.class, studentId)));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM wallet_transactions
                WHERE idempotency_key = ?
                  AND transaction_type = 'REFUND'
                  AND direction = 'IN'
                  AND amount = 250000.00
                """, "wallet-refund:" + refundRequestId));
    }

    @Test
    void concurrentStudentWithdrawalsCannotReserveTheSameRefundBalanceTwice() throws Exception {
        UUID studentId = createStudentProfile("student-withdrawal-race");
        UUID walletId = UUID.randomUUID();
        BigDecimal withdrawalAmount = new BigDecimal("200000.00");
        jdbcTemplate.update("""
                INSERT INTO wallets (
                    id, owner_type, student_id, balance, frozen_balance,
                    withdrawable_balance, frozen_withdrawable_balance, currency, frozen
                ) VALUES (?, 'STUDENT', ?, 300000.00, 0, 300000.00, 0, 'VND', FALSE)
                """, walletId, studentId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 2; index++) {
                UUID withdrawalId = UUID.randomUUID();
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return studentWalletService.reserveForWithdrawal(
                            studentId,
                            withdrawalId,
                            withdrawalAmount).getId();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int successfulReservations = 0;
            int rejectedReservations = 0;
            for (Future<UUID> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successfulReservations++;
                } catch (Exception exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof BusinessException) {
                        rejectedReservations++;
                    } else {
                        throw exception;
                    }
                }
            }
            assertEquals(1, successfulReservations);
            assertEquals(1, rejectedReservations);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(0, new BigDecimal("200000.00").compareTo(jdbcTemplate.queryForObject(
                "SELECT frozen_balance FROM wallets WHERE id = ?",
                BigDecimal.class,
                walletId)));
        assertEquals(0, new BigDecimal("200000.00").compareTo(jdbcTemplate.queryForObject(
                "SELECT frozen_withdrawable_balance FROM wallets WHERE id = ?",
                BigDecimal.class,
                walletId)));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM wallet_transactions
                WHERE wallet_id = ?
                  AND transaction_type = 'WITHDRAWAL_RESERVATION'
                  AND amount = 200000.00
                """, walletId));
    }

    @Test
    void checkoutTransactionRollsBackNewOrderWhenWalletBalanceIsInsufficient() {
        UUID orderId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        String orderCode = "WI-" + orderId;
        BigDecimal gross = new BigDecimal("250000.00");

        jdbcTemplate.update("""
                INSERT INTO wallets (
                    id, owner_type, student_id, balance, frozen_balance, currency, frozen
                )
                VALUES (?, 'STUDENT', ?, 100000.00, 0, 'VND', FALSE)
                """, walletId, STUDENT_ID);

        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

            assertThrows(BusinessException.class, () -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("""
                        INSERT INTO orders (
                            id, student_id, order_code, total_amount, currency, order_status
                        )
                        VALUES (?, ?, ?, ?, 'VND', 'PENDING')
                        """, orderId, STUDENT_ID, orderCode, gross);

                paymentService.payWithWallet(orderId);
            }));

            assertEquals(0, count("SELECT COUNT(*) FROM orders WHERE id = ?", orderId));
            assertEquals(0, count("SELECT COUNT(*) FROM wallet_payment_reservations WHERE order_id = ?", orderId));
        } finally {
            jdbcTemplate.update("DELETE FROM wallets WHERE id = ?", walletId);
        }
    }

    @Test
    void lateSuccessfulIpnCannotReopenCancelledOrder() {
        UUID studentId = createStudentProfile("late-ipn");
        UUID orderId = UUID.randomUUID();
        String orderCode = "LATE-IPN-" + orderId;
        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, student_id, order_code, total_amount, currency, order_status
                ) VALUES (?, ?, ?, 250000.00, 'VND', 'CANCELLED')
                """, orderId, studentId, orderCode);

        when(paymentGateway.getProvider()).thenReturn("VNPAY");
        when(paymentGateway.parseCallback(anyMap())).thenReturn(new PaymentCallbackResult(
                true,
                orderCode,
                "LATE-TX-" + orderId,
                25_000_000L,
                "00",
                "00",
                true));

        assertEquals("02", paymentService.handleIpn(Map.of("vnp_TxnRef", orderCode)).rspCode());
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT order_status FROM orders WHERE id = ?", String.class, orderId));
        assertEquals(0, count("SELECT COUNT(*) FROM payment_transactions WHERE order_id = ?", orderId));
        assertEquals(0, count("SELECT COUNT(*) FROM enrollments WHERE student_id = ?", studentId));
    }

    @Test
    void pendingVnPayPaymentExpiresAndCancelsOrder() {
        UUID studentId = createStudentProfile("expiry");
        UUID orderId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        String orderCode = "EXPIRY-" + orderId;
        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, student_id, order_code, total_amount, currency, order_status, created_at
                ) VALUES (?, ?, ?, 250000.00, 'VND', 'PENDING', NOW() - INTERVAL '16 minutes')
                """, orderId, studentId, orderCode);
        jdbcTemplate.update("""
                INSERT INTO payment_transactions (
                    id, order_id, provider, amount, status, created_at
                ) VALUES (?, ?, 'VNPAY', 250000.00, 'PENDING', NOW() - INTERVAL '16 minutes')
                """, transactionId, orderId);

        when(paymentGateway.getProvider()).thenReturn("VNPAY");

        paymentService.expirePendingPayments();

        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT order_status FROM orders WHERE id = ?", String.class, orderId));
        assertEquals("FAILED", jdbcTemplate.queryForObject(
                "SELECT status FROM payment_transactions WHERE id = ?", String.class, transactionId));
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private UUID createStudentProfile(String prefix) {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_users (id, email, full_name, provider, user_status)
                VALUES (?, ?, 'Race Test Student', 'GOOGLE', 'ACTIVE')
                """, userId, prefix + "+" + userId + "@test.local");
        jdbcTemplate.update("""
                INSERT INTO student_profiles (id, user_id, display_name)
                VALUES (?, ?, 'Race Test Student')
                """, studentId, userId);
        return studentId;
    }

    private void insertPendingOrder(
            UUID orderId,
            UUID studentId,
            String orderCode,
            BigDecimal total
    ) {
        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, student_id, order_code, total_amount, currency, order_status
                ) VALUES (?, ?, ?, ?, 'VND', 'PENDING')
                """, orderId, studentId, orderCode, total);
    }

    private BigDecimal teacherFrozenBalance() {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(
                    (
                        SELECT frozen_balance
                        FROM wallets
                        WHERE teacher_id = ?
                    ),
                    0
                )
                """, BigDecimal.class, TEACHER_ID);
    }

    private BigDecimal teacherBalance() {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(
                    (
                        SELECT balance
                        FROM wallets
                        WHERE teacher_id = ?
                    ),
                    0
                )
                """, BigDecimal.class, TEACHER_ID);
    }
}
