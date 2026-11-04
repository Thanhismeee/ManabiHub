package com.manabihub.payout.service.impl;

import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.payout.entity.BankAccountSnapshot;
import com.manabihub.payout.entity.PayoutSettlement;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.enums.PayoutStatus;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.payout.service.PayoutReconciliationService;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.EscrowStatus;
import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutReconciliationServiceImpl implements PayoutReconciliationService {

    private static final String WITHDRAWAL_REFERENCE = "WITHDRAWAL_REQUEST";

    private final WalletTransactionRepository walletTransactionRepository;
    private final EscrowLedgerRepository escrowLedgerRepository;

    @Override
    public ReconciliationResult reconcile(
            WithdrawalRequest request,
            Wallet wallet,
            TeacherProfile teacher
    ) {
        List<ReconciliationAlert> critical = new ArrayList<>();
        List<ReconciliationAlert> warnings = new ArrayList<>();

        boolean accountBlocked = teacher.getUser() == null
                || teacher.getUser().getUserStatus() != UserStatus.ACTIVE;
        if (accountBlocked) {
            critical.add(alert(
                    "PAYOUT_TEACHER_ACCOUNT_BLOCKED",
                    "CRITICAL",
                    "Teacher account is not active."
            ));
        }

        if (wallet.getTeacher() == null || !wallet.getTeacher().getId().equals(request.getTeacherId())) {
            critical.add(alert(
                    "PAYOUT_WALLET_OWNER_MISMATCH",
                    "CRITICAL",
                    "The wallet does not belong to the withdrawal owner."
            ));
        }

        if (wallet.isFrozen()) {
            critical.add(alert(
                    "PAYOUT_WALLET_FROZEN",
                    "CRITICAL",
                    "The teacher wallet is frozen."
            ));
        }

        if (request.getRequestedAmount() == null
                || request.getRequestedAmount().signum() <= 0) {
            critical.add(alert(
                    "PAYOUT_INVALID_REQUEST_AMOUNT",
                    "CRITICAL",
                    "The withdrawal amount is invalid."
            ));
        } else if (wallet.getFrozenBalance().compareTo(request.getRequestedAmount()) < 0) {
            critical.add(alert(
                    "PAYOUT_RESERVED_BALANCE_MISMATCH",
                    "CRITICAL",
                    "Reserved balance is lower than the withdrawal amount."
            ));
        }

        if (!"VND".equalsIgnoreCase(wallet.getCurrency())) {
            critical.add(alert(
                    "PAYOUT_CURRENCY_MISMATCH",
                    "CRITICAL",
                    "The wallet currency does not match the payout currency."
            ));
        }

        BankAccountSnapshot bank = request.getBankAccountSnapshot();
        if (bank == null
                || isBlank(bank.getBankName())
                || isBlank(bank.getAccountHolderName())
                || isBlank(bank.getAccountNumber())) {
            critical.add(alert(
                    "PAYOUT_BANK_DESTINATION_MISSING",
                    "CRITICAL",
                    "Verified destination bank information is incomplete."
            ));
        }

        Optional<WalletTransaction> reservation = walletTransactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        WITHDRAWAL_REFERENCE,
                        request.getId(),
                        WalletTransactionType.WITHDRAWAL_RESERVATION
                );
        if (reservation.isEmpty()) {
            critical.add(alert(
                    "PAYOUT_RESERVATION_LEDGER_MISSING",
                    "CRITICAL",
                    "The withdrawal reservation ledger entry is missing."
            ));
        } else if (!matchesReservation(reservation.get(), request, wallet)) {
            critical.add(alert(
                    "PAYOUT_RESERVATION_LEDGER_MISMATCH",
                    "CRITICAL",
                    "The withdrawal reservation ledger amount is inconsistent."
            ));
        }

        BigDecimal pendingClearing = escrowLedgerRepository.sumAmountByTeacherIdAndStatus(
                request.getTeacherId(),
                EscrowStatus.HELD
        );
        if (pendingClearing == null) {
            pendingClearing = BigDecimal.ZERO;
        }
        if (pendingClearing.signum() > 0) {
            warnings.add(alert(
                    "PAYOUT_PENDING_ESCROW_PRESENT",
                    "WARNING",
                    "The teacher has other revenue that is still pending escrow clearing."
            ));
        }

        List<ReconciliationAlert> alerts = new ArrayList<>(critical);
        alerts.addAll(warnings);
        ReconciliationStatus status = !critical.isEmpty()
                ? ReconciliationStatus.CRITICAL_MISMATCH
                : warnings.isEmpty() ? ReconciliationStatus.MATCHED : ReconciliationStatus.WARNING;

        return new ReconciliationResult(
                status,
                List.copyOf(alerts),
                pendingClearing,
                pendingClearing.signum() > 0 ? "PARTIALLY_PENDING" : "CLEARED",
                accountBlocked
        );
    }

    @Override
    public ReconciliationResult reconcileStudent(
            WithdrawalRequest request,
            Wallet wallet,
            StudentProfile student
    ) {
        List<ReconciliationAlert> critical = new ArrayList<>();

        boolean accountBlocked = student.getUser() == null
                || student.getUser().getUserStatus() != AccountStatus.ACTIVE;
        if (accountBlocked) {
            critical.add(alert(
                    "PAYOUT_STUDENT_ACCOUNT_BLOCKED",
                    "CRITICAL",
                    "Student account is not active."
            ));
        }
        if (!student.getId().equals(wallet.getStudentId())
                || !student.getId().equals(request.getStudentId())) {
            critical.add(alert(
                    "PAYOUT_WALLET_OWNER_MISMATCH",
                    "CRITICAL",
                    "The wallet does not belong to the withdrawal owner."
            ));
        }
        if (request.getWalletId() != null && !request.getWalletId().equals(wallet.getId())) {
            critical.add(alert(
                    "PAYOUT_REQUEST_WALLET_MISMATCH",
                    "CRITICAL",
                    "The withdrawal request references a different wallet."
            ));
        }
        if (wallet.isFrozen()) {
            critical.add(alert(
                    "PAYOUT_WALLET_FROZEN",
                    "CRITICAL",
                    "The student wallet is frozen."
            ));
        }

        BigDecimal amount = request.getRequestedAmount();
        if (amount == null || amount.signum() <= 0) {
            critical.add(alert(
                    "PAYOUT_INVALID_REQUEST_AMOUNT",
                    "CRITICAL",
                    "The withdrawal amount is invalid."
            ));
        } else if (wallet.getFrozenBalance().compareTo(amount) < 0
                || wallet.getFrozenWithdrawableBalance().compareTo(amount) < 0) {
            critical.add(alert(
                    "PAYOUT_RESERVED_BALANCE_MISMATCH",
                    "CRITICAL",
                    "Reserved withdrawable balance is lower than the withdrawal amount."
            ));
        }
        if (!"VND".equalsIgnoreCase(wallet.getCurrency())) {
            critical.add(alert(
                    "PAYOUT_CURRENCY_MISMATCH",
                    "CRITICAL",
                    "The wallet currency does not match the payout currency."
            ));
        }

        BankAccountSnapshot bank = request.getBankAccountSnapshot();
        if (bank == null
                || isBlank(bank.getBankName())
                || isBlank(bank.getAccountHolderName())
                || isBlank(bank.getAccountNumber())) {
            critical.add(alert(
                    "PAYOUT_BANK_DESTINATION_MISSING",
                    "CRITICAL",
                    "Verified destination bank information is incomplete."
            ));
        }

        Optional<WalletTransaction> reservation = walletTransactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        WITHDRAWAL_REFERENCE,
                        request.getId(),
                        WalletTransactionType.WITHDRAWAL_RESERVATION
                );
        if (reservation.isEmpty()) {
            critical.add(alert(
                    "PAYOUT_RESERVATION_LEDGER_MISSING",
                    "CRITICAL",
                    "The withdrawal reservation ledger entry is missing."
            ));
        } else if (!matchesReservation(reservation.get(), request, wallet)) {
            critical.add(alert(
                    "PAYOUT_RESERVATION_LEDGER_MISMATCH",
                    "CRITICAL",
                    "The withdrawal reservation ledger amount is inconsistent."
            ));
        }

        ReconciliationStatus status = critical.isEmpty()
                ? ReconciliationStatus.MATCHED
                : ReconciliationStatus.CRITICAL_MISMATCH;
        return new ReconciliationResult(
                status,
                List.copyOf(critical),
                BigDecimal.ZERO,
                "NOT_APPLICABLE",
                accountBlocked
        );
    }

    @Override
    public ReconciliationResult reconcileCompleted(
            WithdrawalRequest request,
            Wallet wallet,
            PayoutSettlement settlement
    ) {
        List<ReconciliationAlert> critical = new ArrayList<>();
        WalletOwnerType ownerType = ownerType(request);
        boolean accountBlocked = terminalOwnerAccountBlocked(ownerType, wallet);

        validateTerminalOwner(request, wallet, ownerType, critical);
        validateCompletedRequest(request, wallet, critical);
        validateCompletedSettlement(request, wallet, settlement, ownerType, critical);
        validateLedgerEntry(
                request,
                wallet,
                WalletTransactionType.WITHDRAWAL_RESERVATION,
                "PAYOUT_RESERVATION_LEDGER_MISSING",
                "The withdrawal reservation ledger entry is missing.",
                "PAYOUT_RESERVATION_LEDGER_MISMATCH",
                "The withdrawal reservation ledger amount is inconsistent.",
                critical
        );
        validateLedgerEntry(
                request,
                wallet,
                WalletTransactionType.WITHDRAWAL_COMPLETED,
                "PAYOUT_COMPLETION_LEDGER_MISSING",
                "The completed withdrawal ledger entry is missing.",
                "PAYOUT_COMPLETION_LEDGER_MISMATCH",
                "The completed withdrawal ledger amount is inconsistent.",
                critical
        );

        BigDecimal pendingClearing = ownerType == WalletOwnerType.TEACHER
                ? pendingClearing(request.getTeacherId())
                : BigDecimal.ZERO;
        ReconciliationStatus status = critical.isEmpty()
                ? ReconciliationStatus.MATCHED
                : ReconciliationStatus.CRITICAL_MISMATCH;
        return new ReconciliationResult(
                status,
                List.copyOf(critical),
                pendingClearing,
                ownerType == WalletOwnerType.STUDENT
                        ? "NOT_APPLICABLE"
                        : pendingClearing.signum() > 0 ? "PARTIALLY_PENDING" : "CLEARED",
                accountBlocked
        );
    }

    @Override
    public ReconciliationResult reconcileRejected(
            WithdrawalRequest request,
            Wallet wallet,
            PayoutSettlement settlement
    ) {
        List<ReconciliationAlert> critical = new ArrayList<>();
        WalletOwnerType ownerType = ownerType(request);
        boolean accountBlocked = terminalOwnerAccountBlocked(ownerType, wallet);

        validateTerminalOwner(request, wallet, ownerType, critical);
        validateRejectedRequest(request, wallet, critical);
        validateRejectedSettlement(request, wallet, settlement, ownerType, critical);
        validateLedgerEntry(
                request,
                wallet,
                WalletTransactionType.WITHDRAWAL_RESERVATION,
                WalletDirection.OUT,
                "PAYOUT_RESERVATION_LEDGER_MISSING",
                "The withdrawal reservation ledger entry is missing.",
                "PAYOUT_RESERVATION_LEDGER_MISMATCH",
                "The withdrawal reservation ledger amount is inconsistent.",
                critical
        );
        validateLedgerEntry(
                request,
                wallet,
                WalletTransactionType.WITHDRAWAL_REJECTED,
                WalletDirection.IN,
                "PAYOUT_REJECTION_LEDGER_MISSING",
                "The rejected withdrawal has no reservation release ledger entry.",
                "PAYOUT_REJECTION_LEDGER_MISMATCH",
                "The rejected withdrawal release ledger amount is inconsistent.",
                critical
        );
        if (walletTransactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        WITHDRAWAL_REFERENCE,
                        request.getId(),
                        WalletTransactionType.WITHDRAWAL_COMPLETED
                )
                .isPresent()) {
            critical.add(alert(
                    "PAYOUT_REJECTED_COMPLETION_CONFLICT",
                    "CRITICAL",
                    "The rejected withdrawal also has a completed payout ledger entry."
            ));
        }

        BigDecimal pendingClearing = ownerType == WalletOwnerType.TEACHER
                ? pendingClearing(request.getTeacherId())
                : BigDecimal.ZERO;
        ReconciliationStatus status = critical.isEmpty()
                ? ReconciliationStatus.MATCHED
                : ReconciliationStatus.CRITICAL_MISMATCH;
        return new ReconciliationResult(
                status,
                List.copyOf(critical),
                pendingClearing,
                ownerType == WalletOwnerType.STUDENT
                        ? "NOT_APPLICABLE"
                        : pendingClearing.signum() > 0 ? "PARTIALLY_PENDING" : "CLEARED",
                accountBlocked
        );
    }

    private void validateTerminalOwner(
            WithdrawalRequest request,
            Wallet wallet,
            WalletOwnerType ownerType,
            List<ReconciliationAlert> critical
    ) {
        boolean ownerMatches = ownerType == WalletOwnerType.STUDENT
                ? request.getStudentId() != null
                    && request.getStudentId().equals(wallet.getStudentId())
                : request.getTeacherId() != null
                    && wallet.getTeacher() != null
                    && request.getTeacherId().equals(wallet.getTeacher().getId());
        if (!ownerMatches) {
            critical.add(alert(
                    "PAYOUT_WALLET_OWNER_MISMATCH",
                    "CRITICAL",
                    "The wallet does not belong to the withdrawal owner."
            ));
        }
        if (request.getWalletId() != null && !request.getWalletId().equals(wallet.getId())) {
            critical.add(alert(
                    "PAYOUT_REQUEST_WALLET_MISMATCH",
                    "CRITICAL",
                    "The withdrawal request references a different wallet."
            ));
        }
    }

    private void validateRejectedRequest(
            WithdrawalRequest request,
            Wallet wallet,
            List<ReconciliationAlert> critical
    ) {
        if (request.getStatus() != WithdrawalStatus.REJECTED) {
            critical.add(alert(
                    "PAYOUT_REJECTED_STATE_MISMATCH",
                    "CRITICAL",
                    "The withdrawal request is not marked as rejected."
            ));
        }
        if (request.getRequestedAmount() == null
                || request.getRequestedAmount().signum() <= 0) {
            critical.add(alert(
                    "PAYOUT_INVALID_REQUEST_AMOUNT",
                    "CRITICAL",
                    "The withdrawal amount is invalid."
            ));
        }
        if (wallet.getBalance() == null || wallet.getBalance().signum() < 0
                || wallet.getFrozenBalance() == null
                || wallet.getFrozenBalance().signum() < 0) {
            critical.add(alert(
                    "PAYOUT_WALLET_BALANCE_INVALID",
                    "CRITICAL",
                    "The wallet contains a negative or missing balance."
            ));
        }
        if (!"VND".equalsIgnoreCase(wallet.getCurrency())) {
            critical.add(alert(
                    "PAYOUT_CURRENCY_MISMATCH",
                    "CRITICAL",
                    "The wallet currency does not match the payout currency."
            ));
        }
    }

    private void validateRejectedSettlement(
            WithdrawalRequest request,
            Wallet wallet,
            PayoutSettlement settlement,
            WalletOwnerType ownerType,
            List<ReconciliationAlert> critical
    ) {
        if (settlement == null) {
            critical.add(alert(
                    "PAYOUT_REJECTED_STATE_MISMATCH",
                    "CRITICAL",
                    "The rejected withdrawal has no matching settlement decision."
            ));
            return;
        }
        if (settlement.getStatus() != PayoutStatus.REJECTED
                || !request.getId().equals(settlement.getWithdrawalRequestId())
                || !"REJECTED".equalsIgnoreCase(settlement.getDecision())) {
            critical.add(alert(
                    "PAYOUT_REJECTED_STATE_MISMATCH",
                    "CRITICAL",
                    "The withdrawal request and settlement rejection states are inconsistent."
            ));
        }
        if (!wallet.getId().equals(settlement.getWalletId())) {
            critical.add(alert(
                    "PAYOUT_SETTLEMENT_WALLET_MISMATCH",
                    "CRITICAL",
                    "The payout settlement references a different wallet."
            ));
        }
        if (settlement.getOwnerType() != null && settlement.getOwnerType() != ownerType) {
            critical.add(alert(
                    "PAYOUT_SETTLEMENT_OWNER_MISMATCH",
                    "CRITICAL",
                    "The payout settlement references a different owner type."
            ));
        }
        if (request.getRequestedAmount() == null
                || settlement.getAmount() == null
                || settlement.getAmount().compareTo(request.getRequestedAmount()) != 0) {
            critical.add(alert(
                    "PAYOUT_SETTLEMENT_AMOUNT_MISMATCH",
                    "CRITICAL",
                    "The payout settlement amount differs from the withdrawal amount."
            ));
        }
        if (isBlank(settlement.getCurrency())
                || !settlement.getCurrency().equalsIgnoreCase(wallet.getCurrency())) {
            critical.add(alert(
                    "PAYOUT_SETTLEMENT_CURRENCY_MISMATCH",
                    "CRITICAL",
                    "The payout settlement currency differs from the wallet currency."
            ));
        }
        if (isBlank(settlement.getDecisionReason())) {
            critical.add(alert(
                    "PAYOUT_REJECTION_REASON_MISSING",
                    "CRITICAL",
                    "The rejected payout has no decision reason."
            ));
        }
        if (!isBlank(settlement.getProviderReferenceId())) {
            critical.add(alert(
                    "PAYOUT_REJECTED_TRANSFER_CONFLICT",
                    "CRITICAL",
                    "The rejected payout already has a provider transaction reference."
            ));
        }
    }

    private void validateCompletedRequest(
            WithdrawalRequest request,
            Wallet wallet,
            List<ReconciliationAlert> critical
    ) {
        if (request.getStatus() != WithdrawalStatus.EXECUTED) {
            critical.add(alert(
                    "PAYOUT_COMPLETED_STATE_MISMATCH",
                    "CRITICAL",
                    "The withdrawal request is not marked as executed."
            ));
        }
        if (request.getRequestedAmount() == null
                || request.getRequestedAmount().signum() <= 0) {
            critical.add(alert(
                    "PAYOUT_INVALID_REQUEST_AMOUNT",
                    "CRITICAL",
                    "The withdrawal amount is invalid."
            ));
        }
        if (wallet.getBalance() == null || wallet.getBalance().signum() < 0
                || wallet.getFrozenBalance() == null
                || wallet.getFrozenBalance().signum() < 0) {
            critical.add(alert(
                    "PAYOUT_WALLET_BALANCE_INVALID",
                    "CRITICAL",
                    "The wallet contains a negative or missing balance."
            ));
        }
        if (!"VND".equalsIgnoreCase(wallet.getCurrency())) {
            critical.add(alert(
                    "PAYOUT_CURRENCY_MISMATCH",
                    "CRITICAL",
                    "The wallet currency does not match the payout currency."
            ));
        }
        BankAccountSnapshot bank = request.getBankAccountSnapshot();
        if (bank == null
                || isBlank(bank.getBankName())
                || isBlank(bank.getAccountHolderName())
                || isBlank(bank.getAccountNumber())) {
            critical.add(alert(
                    "PAYOUT_BANK_DESTINATION_MISSING",
                    "CRITICAL",
                    "Verified destination bank information is incomplete."
            ));
        }
    }

    private void validateCompletedSettlement(
            WithdrawalRequest request,
            Wallet wallet,
            PayoutSettlement settlement,
            WalletOwnerType ownerType,
            List<ReconciliationAlert> critical
    ) {
        if (settlement == null
                || settlement.getStatus() != PayoutStatus.SUCCEEDED
                || !request.getId().equals(settlement.getWithdrawalRequestId())) {
            critical.add(alert(
                    "PAYOUT_COMPLETED_STATE_MISMATCH",
                    "CRITICAL",
                    "The payout settlement is not in a completed state."
            ));
            return;
        }
        if (!wallet.getId().equals(settlement.getWalletId())) {
            critical.add(alert(
                    "PAYOUT_SETTLEMENT_WALLET_MISMATCH",
                    "CRITICAL",
                    "The payout settlement references a different wallet."
            ));
        }
        if (settlement.getOwnerType() != null && settlement.getOwnerType() != ownerType) {
            critical.add(alert(
                    "PAYOUT_SETTLEMENT_OWNER_MISMATCH",
                    "CRITICAL",
                    "The payout settlement references a different owner type."
            ));
        }
        if (request.getRequestedAmount() == null
                || settlement.getAmount() == null
                || settlement.getAmount().compareTo(request.getRequestedAmount()) != 0) {
            critical.add(alert(
                    "PAYOUT_SETTLEMENT_AMOUNT_MISMATCH",
                    "CRITICAL",
                    "The payout settlement amount differs from the withdrawal amount."
            ));
        }
        if (isBlank(settlement.getCurrency())
                || !settlement.getCurrency().equalsIgnoreCase(wallet.getCurrency())) {
            critical.add(alert(
                    "PAYOUT_SETTLEMENT_CURRENCY_MISMATCH",
                    "CRITICAL",
                    "The payout settlement currency differs from the wallet currency."
            ));
        }
        if (isBlank(settlement.getProviderReferenceId())) {
            critical.add(alert(
                    "PAYOUT_PROVIDER_REFERENCE_MISSING",
                    "CRITICAL",
                    "The completed payout has no provider transaction reference."
            ));
        }
    }

    private void validateLedgerEntry(
            WithdrawalRequest request,
            Wallet wallet,
            WalletTransactionType transactionType,
            String missingCode,
            String missingMessage,
            String mismatchCode,
            String mismatchMessage,
            List<ReconciliationAlert> critical
    ) {
        validateLedgerEntry(
                request,
                wallet,
                transactionType,
                WalletDirection.OUT,
                missingCode,
                missingMessage,
                mismatchCode,
                mismatchMessage,
                critical
        );
    }

    private void validateLedgerEntry(
            WithdrawalRequest request,
            Wallet wallet,
            WalletTransactionType transactionType,
            WalletDirection expectedDirection,
            String missingCode,
            String missingMessage,
            String mismatchCode,
            String mismatchMessage,
            List<ReconciliationAlert> critical
    ) {
        Optional<WalletTransaction> transaction = walletTransactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        WITHDRAWAL_REFERENCE,
                        request.getId(),
                        transactionType
                );
        if (transaction.isEmpty()) {
            critical.add(alert(missingCode, "CRITICAL", missingMessage));
        } else if (!matchesLedgerEntry(
                transaction.get(), request, wallet, expectedDirection)) {
            critical.add(alert(mismatchCode, "CRITICAL", mismatchMessage));
        }
    }

    private WalletOwnerType ownerType(WithdrawalRequest request) {
        if (request.getOwnerType() != null) {
            return request.getOwnerType();
        }
        return request.getStudentId() == null
                ? WalletOwnerType.TEACHER
                : WalletOwnerType.STUDENT;
    }

    private boolean terminalOwnerAccountBlocked(WalletOwnerType ownerType, Wallet wallet) {
        if (ownerType == WalletOwnerType.STUDENT) {
            return wallet.getStudent() == null
                    || wallet.getStudent().getUser() == null
                    || wallet.getStudent().getUser().getUserStatus() != AccountStatus.ACTIVE;
        }
        return wallet.getTeacher() == null
                || wallet.getTeacher().getUser() == null
                || wallet.getTeacher().getUser().getUserStatus() != UserStatus.ACTIVE;
    }

    private BigDecimal pendingClearing(UUID teacherId) {
        BigDecimal amount = escrowLedgerRepository.sumAmountByTeacherIdAndStatus(
                teacherId,
                EscrowStatus.HELD
        );
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private boolean matchesReservation(
            WalletTransaction reservation,
            WithdrawalRequest request,
            Wallet wallet
    ) {
        return matchesLedgerEntry(
                reservation, request, wallet, WalletDirection.OUT);
    }

    private boolean matchesLedgerEntry(
            WalletTransaction transaction,
            WithdrawalRequest request,
            Wallet wallet,
            WalletDirection expectedDirection
    ) {
        return request.getRequestedAmount() != null
                && transaction.getWalletId() != null
                && transaction.getWalletId().equals(wallet.getId())
                && transaction.getDirection() == expectedDirection
                && transaction.getAmount() != null
                && transaction.getAmount().abs().compareTo(request.getRequestedAmount()) == 0;
    }

    private ReconciliationAlert alert(String code, String severity, String message) {
        return new ReconciliationAlert(code, severity, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
