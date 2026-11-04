package com.manabihub.refund.service.impl;

import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.order.entity.Order;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.refund.entity.RefundRequest;
import com.manabihub.refund.enums.RefundStatus;
import com.manabihub.refund.repository.RefundRequestRepository;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import com.manabihub.wallet.service.StudentWalletService;
import com.manabihub.wallet.service.impl.StudentWalletServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real JPA transactions and student-wallet credits; the decision stub injects
 * a failure after credit to exercise rollback and concurrent worker delivery. */
@DataJpaTest(showSql = false)
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
        replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AutomaticRefundTransactionService.class, StudentWalletServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AutomaticRefundTransactionIntegrationTest {
    @Autowired AutomaticRefundTransactionService worker;
    @Autowired StudentWalletService wallets;
    @Autowired RefundRequestRepository refunds;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletTransactionRepository ledger;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean RefundDecisionTransactionService decisions;
    @MockBean CommercialPolicyService policy;
    UUID studentId;
    UUID refundId;
    Instant scheduledAt;

    @BeforeEach
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            AppUser user = AppUser.builder().email(UUID.randomUUID() + "@test.invalid")
                    .fullName("Refund test").build();
            entityManager.persist(user);
            StudentProfile student = StudentProfile.builder().user(user).build();
            entityManager.persist(student);
            studentId = student.getId();
            Order order = Order.builder().student(student).orderCode(UUID.randomUUID().toString())
                    .totalAmount(new BigDecimal("169000")).status(OrderStatus.PAID).build();
            entityManager.persist(order);
            entityManager.persist(Wallet.builder().student(student).ownerType(WalletOwnerType.STUDENT).build());
            RefundRequest refund = RefundRequest.builder().student(student).order(order).reason("Test")
                    .autoRefundNextAttemptAt(Instant.now().minusSeconds(1)).build();
            entityManager.persist(refund);
            refundId = refund.getId();
        });
        scheduledAt = refunds.findById(refundId).orElseThrow().getAutoRefundNextAttemptAt();
    }

    @Test
    void failureAfterWalletCreditRollsBackMoneyAndClaimThenPersistsRetrySeparately() {
        when(decisions.autoApproveToStudentWallet(refundId)).thenAnswer(invocation -> {
            wallets.creditRefund(studentId, new BigDecimal("169000"), refundId, "test");
            entityManager.flush();
            throw new CannotAcquireLockException("simulated failure after wallet credit");
        });
        RuntimeException failure = assertThrows(CannotAcquireLockException.class,
                () -> worker.execute(refundId, scheduledAt));
        assertEquals(0, walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, studentId)
                .orElseThrow().getBalance().signum());
        assertTrue(ledger.findByIdempotencyKey("wallet-refund:" + refundId).isEmpty());
        assertEquals(0, refunds.findById(refundId).orElseThrow().getAutoRefundAttemptCount());
        assertEquals(scheduledAt, refunds.findById(refundId).orElseThrow().getAutoRefundNextAttemptAt());

        worker.recordFailure(refundId, scheduledAt, failure);
        var pending = refunds.findById(refundId).orElseThrow();
        assertEquals(1, pending.getAutoRefundAttemptCount());
        assertEquals(RefundStatus.PENDING, pending.getStatus());
        assertTrue(pending.getAutoRefundNextAttemptAt().isAfter(Instant.now()));
    }

    @Test
    void concurrentWorkersCreditOnceAndCommittedWorkIsDiscoverable() throws Exception {
        assertTrue(refunds.findAutomaticRefundsDue(Instant.now(), PageRequest.of(0, 100)).stream()
                .anyMatch(refund -> refund.getId().equals(refundId)));
        when(decisions.autoApproveToStudentWallet(refundId)).thenAnswer(invocation -> {
            wallets.creditRefund(studentId, new BigDecimal("169000"), refundId, "test");
            RefundRequest refund = refunds.findByIdForUpdate(refundId).orElseThrow();
            refund.setStatus(RefundStatus.APPROVED);
            return refund;
        });
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); worker.execute(refundId, scheduledAt); return true; });
            var second = executor.submit(() -> { start.await(); worker.execute(refundId, scheduledAt); return true; });
            start.countDown();
            assertTrue(first.get(15, TimeUnit.SECONDS));
            assertTrue(second.get(15, TimeUnit.SECONDS));
        }
        verify(decisions, times(1)).autoApproveToStudentWallet(refundId);
        assertEquals(0, new BigDecimal("169000").compareTo(walletRepository
                .findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, studentId).orElseThrow().getBalance()));
        assertTrue(ledger.findByIdempotencyKey("wallet-refund:" + refundId).isPresent());
        assertNull(refunds.findById(refundId).orElseThrow().getAutoRefundNextAttemptAt());
    }
}
