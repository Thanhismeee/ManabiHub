package com.manabihub.refund.service.impl;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.refund.entity.RefundRequest;
import com.manabihub.refund.enums.RefundSettlementMethod;
import com.manabihub.refund.enums.RefundSettlementStatus;
import com.manabihub.refund.enums.RefundStatus;
import com.manabihub.refund.repository.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutomaticRefundTransactionService {
    private static final int MAX_ATTEMPTS = 3;
    private final RefundRequestRepository repository;
    private final RefundDecisionTransactionService decisions;

    @Transactional(timeout = 30)
    public void execute(UUID id, Instant scheduledAt) {
        RefundRequest refund = repository.findByIdForUpdate(id).orElse(null);
        if (!isCurrentAttempt(refund, scheduledAt) || scheduledAt.isAfter(Instant.now())) {
            return;
        }
        // Keep claim, wallet credit and completion in one transaction. A restart
        // rolls back the claim too, so another worker can recover the same work.
        refund.setAutoRefundAttemptCount(refund.getAutoRefundAttemptCount() + 1);
        decisions.autoApproveToStudentWallet(id);
        refund.setAutoRefundNextAttemptAt(null);
    }

    // Invoked by the job only after execute has fully rolled back.
    @Transactional(timeout = 10)
    public void recordFailure(UUID id, Instant scheduledAt, RuntimeException failure) {
        RefundRequest refund = repository.findByIdForUpdate(id).orElse(null);
        if (!isCurrentAttempt(refund, scheduledAt)) {
            return; // Another worker or Finance has already handled this attempt.
        }
        int attempts = refund.getAutoRefundAttemptCount() + 1;
        boolean transientFailure = isTransient(failure);
        String code = transientFailure ? "AUTO_REFUND_TRANSIENT_DATABASE_ERROR"
                : failure instanceof BusinessException business
                    ? business.getMessageCode() : "AUTO_REFUND_SETTLEMENT_FAILED";
        refund.setAutoRefundAttemptCount(attempts);
        refund.setAutoRefundLastErrorCode(code);
        if (transientFailure && attempts < MAX_ATTEMPTS) {
            refund.setAutoRefundNextAttemptAt(Instant.now().plusSeconds(5L * attempts));
        } else {
            refund.setAutoRefundNextAttemptAt(null);
            refund.setStatus(RefundStatus.RECONCILIATION_REQUIRED);
            refund.setSettlementMethod(RefundSettlementMethod.WALLET);
            refund.setSettlementStatus(RefundSettlementStatus.FAILED);
            refund.setReconciliationReasonCode(transientFailure
                    ? "AUTO_REFUND_RETRY_EXHAUSTED" : code);
        }
        repository.save(refund);
    }

    private boolean isCurrentAttempt(RefundRequest refund, Instant scheduledAt) {
        return refund != null && refund.getStatus() == RefundStatus.PENDING
                && scheduledAt != null
                && Objects.equals(refund.getAutoRefundNextAttemptAt(), scheduledAt);
    }

    private boolean isTransient(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof CannotCreateTransactionException
                    || cause instanceof TransactionTimedOutException
                    || cause instanceof java.sql.SQLTransientException) {
                return true;
            }
        }
        return false;
    }
}
