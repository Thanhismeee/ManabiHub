package com.manabihub.payout.service;

import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.payout.entity.PayoutSettlement;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.util.List;

public interface PayoutReconciliationService {

    ReconciliationResult reconcile(
            WithdrawalRequest request,
            Wallet wallet,
            TeacherProfile teacher
    );

    ReconciliationResult reconcileStudent(
            WithdrawalRequest request,
            Wallet wallet,
            StudentProfile student
    );

    /**
     * Reconciles a payout after it has been completed. At this phase the reserved
     * balance has already been released, so integrity is established from the
     * immutable settlement and wallet ledger entries instead of requiring the
     * pre-transfer reservation to remain in the wallet balance.
     */
    ReconciliationResult reconcileCompleted(
            WithdrawalRequest request,
            Wallet wallet,
            PayoutSettlement settlement
    );

    /**
     * Reconciles a payout after Finance has rejected it. A valid rejection has
     * already released the reservation, so the release ledger entry replaces the
     * reserved wallet balance as the source of truth for this phase.
     */
    ReconciliationResult reconcileRejected(
            WithdrawalRequest request,
            Wallet wallet,
            PayoutSettlement settlement
    );

    record ReconciliationAlert(String code, String severity, String message) {
    }

    record ReconciliationResult(
            ReconciliationStatus status,
            List<ReconciliationAlert> alerts,
            BigDecimal pendingClearing,
            String escrowStatus,
            boolean teacherAccountBlocked
    ) {
        public boolean hasCriticalMismatch() {
            return status == ReconciliationStatus.CRITICAL_MISMATCH;
        }
    }
}
