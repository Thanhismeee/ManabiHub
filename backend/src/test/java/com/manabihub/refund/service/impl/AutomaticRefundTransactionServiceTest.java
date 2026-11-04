package com.manabihub.refund.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.refund.entity.RefundRequest;
import com.manabihub.refund.enums.RefundStatus;
import com.manabihub.refund.repository.RefundRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomaticRefundTransactionServiceTest {
    @Mock RefundRequestRepository repository;
    @Mock RefundDecisionTransactionService decisions;
    @InjectMocks AutomaticRefundTransactionService service;
    RefundRequest refund;
    Instant scheduledAt;

    @BeforeEach
    void setup() {
        scheduledAt = Instant.now().minusSeconds(1);
        refund = RefundRequest.builder().id(UUID.randomUUID())
                .autoRefundNextAttemptAt(scheduledAt).build();
        when(repository.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
    }

    @Test
    void duplicateWorkerDeliverySettlesOnlyOnce() {
        service.execute(refund.getId(), scheduledAt);
        service.execute(refund.getId(), scheduledAt);
        verify(decisions, times(1)).autoApproveToStudentWallet(refund.getId());
        assertEquals(1, refund.getAutoRefundAttemptCount());
        assertNull(refund.getAutoRefundNextAttemptAt());
    }

    @Test
    void transientFailureRetriesAndStaleWorkerCannotOverwriteRetry() {
        service.recordFailure(refund.getId(), scheduledAt, new CannotAcquireLockException("locked"));
        Instant retryAt = refund.getAutoRefundNextAttemptAt();
        service.recordFailure(refund.getId(), scheduledAt, new IllegalStateException("stale"));
        assertEquals(RefundStatus.PENDING, refund.getStatus());
        assertEquals(1, refund.getAutoRefundAttemptCount());
        assertTrue(retryAt.isAfter(Instant.now()));
        assertEquals(retryAt, refund.getAutoRefundNextAttemptAt());
        assertNull(refund.getReconciliationReasonCode());
    }

    @Test
    void exhaustedRetriesRequireFinanceWithReason() {
        refund.setAutoRefundAttemptCount(2);
        service.recordFailure(refund.getId(), scheduledAt, new CannotAcquireLockException("locked"));
        assertEquals(RefundStatus.RECONCILIATION_REQUIRED, refund.getStatus());
        assertEquals("AUTO_REFUND_RETRY_EXHAUSTED", refund.getReconciliationReasonCode());
        assertEquals(3, refund.getAutoRefundAttemptCount());
        assertNull(refund.getAutoRefundNextAttemptAt());
    }

    @Test
    void financialIntegrityFailureRequiresFinanceImmediately() {
        service.recordFailure(refund.getId(), scheduledAt,
                new BusinessException(MessageCodes.FINANCIAL_INTEGRITY_VIOLATION, "invalid allocation"));
        assertEquals(RefundStatus.RECONCILIATION_REQUIRED, refund.getStatus());
        assertEquals(MessageCodes.FINANCIAL_INTEGRITY_VIOLATION, refund.getReconciliationReasonCode());
        assertNull(refund.getAutoRefundNextAttemptAt());
    }

    @Test
    void completedFinanceDecisionCannotBeOverwrittenByLateFailureOrWorker() {
        refund.setStatus(RefundStatus.APPROVED);
        service.execute(refund.getId(), scheduledAt);
        service.recordFailure(refund.getId(), scheduledAt, new IllegalStateException("late"));
        assertEquals(RefundStatus.APPROVED, refund.getStatus());
        verifyNoInteractions(decisions);
        verify(repository, never()).save(any());
    }

    @Test
    void notYetDueAttemptDoesNotCreditWallet() {
        refund.setAutoRefundNextAttemptAt(Instant.now().plusSeconds(60));
        service.execute(refund.getId(), refund.getAutoRefundNextAttemptAt());
        verifyNoInteractions(decisions);
    }
}
