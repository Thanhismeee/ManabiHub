package com.manabihub.payout.service.impl;

import com.manabihub.kyc.domain.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.payout.entity.BankAccountSnapshot;
import com.manabihub.payout.entity.PayoutSettlement;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.enums.PayoutStatus;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.EscrowStatus;
import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayoutReconciliationServiceImplTest {

    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private EscrowLedgerRepository escrowLedgerRepository;

    private PayoutReconciliationServiceImpl service;
    private WithdrawalRequest request;
    private Wallet wallet;
    private TeacherProfile teacher;
    private WalletTransaction reservation;

    @BeforeEach
    void setUp() {
        UUID teacherId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        request = WithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .teacherId(teacherId)
                .walletId(walletId)
                .requestedAmount(new BigDecimal("500000.00"))
                .bankAccountSnapshot(validBank())
                .build();

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUserStatus(UserStatus.ACTIVE);
        teacher = new TeacherProfile();
        teacher.setId(teacherId);
        teacher.setUser(user);
        wallet = Wallet.builder()
                .id(walletId)
                .teacher(teacher)
                .ownerType(com.manabihub.wallet.enums.WalletOwnerType.TEACHER)
                .balance(new BigDecimal("1000000.00"))
                .frozenBalance(new BigDecimal("500000.00"))
                .currency("VND")
                .build();
        reservation = WalletTransaction.builder()
                .walletId(walletId)
                .amount(new BigDecimal("-500000.00"))
                .direction(WalletDirection.OUT)
                .transactionType(WalletTransactionType.WITHDRAWAL_RESERVATION)
                .build();

        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "WITHDRAWAL_REQUEST",
                request.getId(),
                WalletTransactionType.WITHDRAWAL_RESERVATION
        )).thenReturn(Optional.of(reservation));
        when(escrowLedgerRepository.sumAmountByTeacherIdAndStatus(
                teacherId,
                EscrowStatus.HELD
        )).thenReturn(BigDecimal.ZERO);
        service = new PayoutReconciliationServiceImpl(
                walletTransactionRepository,
                escrowLedgerRepository
        );
    }

    @Test
    void reconciliationMatchesConsistentAccounting() {
        var result = service.reconcile(request, wallet, teacher);

        assertEquals(ReconciliationStatus.MATCHED, result.status());
        assertTrue(result.alerts().isEmpty());
    }

    @Test
    void reconciliationBlocksWrongWalletLedger() {
        reservation.setWalletId(UUID.randomUUID());

        var result = service.reconcile(request, wallet, teacher);

        assertEquals(ReconciliationStatus.CRITICAL_MISMATCH, result.status());
        assertTrue(result.alerts().stream()
                .anyMatch(alert -> "PAYOUT_RESERVATION_LEDGER_MISMATCH".equals(alert.code())));
    }

    @Test
    void reconciliationWarnsButDoesNotBlockOtherPendingEscrow() {
        when(escrowLedgerRepository.sumAmountByTeacherIdAndStatus(
                request.getTeacherId(),
                EscrowStatus.HELD
        )).thenReturn(new BigDecimal("100000.00"));

        var result = service.reconcile(request, wallet, teacher);

        assertEquals(ReconciliationStatus.WARNING, result.status());
        assertEquals(0, result.pendingClearing().compareTo(new BigDecimal("100000.00")));
    }

    @Test
    void reconciliationBlocksLockedTeacher() {
        teacher.getUser().setUserStatus(UserStatus.LOCKED);

        var result = service.reconcile(request, wallet, teacher);

        assertEquals(ReconciliationStatus.CRITICAL_MISMATCH, result.status());
        assertTrue(result.teacherAccountBlocked());
    }

    @Test
    void reconciliationBlocksFrozenWallet() {
        wallet.setFrozen(true);

        var result = service.reconcile(request, wallet, teacher);

        assertEquals(ReconciliationStatus.CRITICAL_MISMATCH, result.status());
        assertTrue(result.alerts().stream()
                .anyMatch(alert -> "PAYOUT_WALLET_FROZEN".equals(alert.code())));
    }

    @Test
    void completedReconciliationAcceptsReleasedReservedBalance() {
        request.setStatus(WithdrawalStatus.EXECUTED);
        wallet.setBalance(new BigDecimal("500000.00"));
        wallet.setFrozenBalance(BigDecimal.ZERO);
        PayoutSettlement settlement = completedSettlement();
        WalletTransaction completion = WalletTransaction.builder()
                .walletId(wallet.getId())
                .amount(request.getRequestedAmount())
                .direction(WalletDirection.OUT)
                .transactionType(WalletTransactionType.WITHDRAWAL_COMPLETED)
                .build();
        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "WITHDRAWAL_REQUEST",
                request.getId(),
                WalletTransactionType.WITHDRAWAL_COMPLETED
        )).thenReturn(Optional.of(completion));

        var result = service.reconcileCompleted(request, wallet, settlement);

        assertEquals(ReconciliationStatus.MATCHED, result.status());
        assertTrue(result.alerts().stream()
                .noneMatch(alert -> "PAYOUT_RESERVED_BALANCE_MISMATCH".equals(alert.code())));
    }

    @Test
    void completedReconciliationBlocksMissingCompletionLedger() {
        request.setStatus(WithdrawalStatus.EXECUTED);
        wallet.setBalance(new BigDecimal("500000.00"));
        wallet.setFrozenBalance(BigDecimal.ZERO);
        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "WITHDRAWAL_REQUEST",
                request.getId(),
                WalletTransactionType.WITHDRAWAL_COMPLETED
        )).thenReturn(Optional.empty());

        var result = service.reconcileCompleted(request, wallet, completedSettlement());

        assertEquals(ReconciliationStatus.CRITICAL_MISMATCH, result.status());
        assertTrue(result.alerts().stream()
                .anyMatch(alert -> "PAYOUT_COMPLETION_LEDGER_MISSING".equals(alert.code())));
    }

    @Test
    void rejectedReconciliationMatchesReleasedReservation() {
        request.setStatus(WithdrawalStatus.REJECTED);
        wallet.setFrozenBalance(BigDecimal.ZERO);
        WalletTransaction release = WalletTransaction.builder()
                .walletId(wallet.getId())
                .amount(request.getRequestedAmount())
                .direction(WalletDirection.IN)
                .transactionType(WalletTransactionType.WITHDRAWAL_REJECTED)
                .build();
        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "WITHDRAWAL_REQUEST",
                request.getId(),
                WalletTransactionType.WITHDRAWAL_REJECTED
        )).thenReturn(Optional.of(release));

        var result = service.reconcileRejected(request, wallet, rejectedSettlement());

        assertEquals(ReconciliationStatus.MATCHED, result.status());
        assertTrue(result.alerts().isEmpty());
    }

    @Test
    void rejectedReconciliationBlocksMissingReleaseLedger() {
        request.setStatus(WithdrawalStatus.REJECTED);
        wallet.setFrozenBalance(BigDecimal.ZERO);

        var result = service.reconcileRejected(request, wallet, rejectedSettlement());

        assertEquals(ReconciliationStatus.CRITICAL_MISMATCH, result.status());
        assertTrue(result.alerts().stream()
                .anyMatch(alert -> "PAYOUT_REJECTION_LEDGER_MISSING".equals(alert.code())));
    }

    @Test
    void rejectedReconciliationBlocksCompletedLedgerConflict() {
        request.setStatus(WithdrawalStatus.REJECTED);
        wallet.setFrozenBalance(BigDecimal.ZERO);
        WalletTransaction release = WalletTransaction.builder()
                .walletId(wallet.getId())
                .amount(request.getRequestedAmount())
                .direction(WalletDirection.IN)
                .transactionType(WalletTransactionType.WITHDRAWAL_REJECTED)
                .build();
        WalletTransaction completion = WalletTransaction.builder()
                .walletId(wallet.getId())
                .amount(request.getRequestedAmount())
                .direction(WalletDirection.OUT)
                .transactionType(WalletTransactionType.WITHDRAWAL_COMPLETED)
                .build();
        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "WITHDRAWAL_REQUEST",
                request.getId(),
                WalletTransactionType.WITHDRAWAL_REJECTED
        )).thenReturn(Optional.of(release));
        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "WITHDRAWAL_REQUEST",
                request.getId(),
                WalletTransactionType.WITHDRAWAL_COMPLETED
        )).thenReturn(Optional.of(completion));

        var result = service.reconcileRejected(request, wallet, rejectedSettlement());

        assertEquals(ReconciliationStatus.CRITICAL_MISMATCH, result.status());
        assertTrue(result.alerts().stream()
                .anyMatch(alert -> "PAYOUT_REJECTED_COMPLETION_CONFLICT".equals(alert.code())));
    }

    @Test
    void studentReconciliationMatchesReservedWithdrawableBalance() {
        UUID studentId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        com.manabihub.identity.entity.AppUser studentUser =
                new com.manabihub.identity.entity.AppUser();
        studentUser.setId(UUID.randomUUID());
        studentUser.setUserStatus(AccountStatus.ACTIVE);
        StudentProfile student = new StudentProfile();
        student.setId(studentId);
        student.setUser(studentUser);
        request = WithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .ownerType(WalletOwnerType.STUDENT)
                .studentId(studentId)
                .walletId(walletId)
                .requestedAmount(new BigDecimal("200000.00"))
                .bankAccountSnapshot(validBank())
                .build();
        wallet = Wallet.builder()
                .id(walletId)
                .ownerType(WalletOwnerType.STUDENT)
                .student(student)
                .balance(new BigDecimal("300000.00"))
                .withdrawableBalance(new BigDecimal("300000.00"))
                .frozenBalance(new BigDecimal("200000.00"))
                .frozenWithdrawableBalance(new BigDecimal("200000.00"))
                .currency("VND")
                .build();
        reservation = WalletTransaction.builder()
                .walletId(walletId)
                .amount(new BigDecimal("200000.00"))
                .direction(WalletDirection.OUT)
                .transactionType(WalletTransactionType.WITHDRAWAL_RESERVATION)
                .build();
        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "WITHDRAWAL_REQUEST",
                request.getId(),
                WalletTransactionType.WITHDRAWAL_RESERVATION
        )).thenReturn(Optional.of(reservation));

        var result = service.reconcileStudent(request, wallet, student);

        assertEquals(ReconciliationStatus.MATCHED, result.status());
        assertTrue(result.alerts().isEmpty());
    }

    private BankAccountSnapshot validBank() {
        BankAccountSnapshot bank = new BankAccountSnapshot();
        bank.setBankName("Vietcombank");
        bank.setAccountHolderName("NGUYEN SENSEI");
        bank.setAccountNumber("0123456789");
        return bank;
    }

    private PayoutSettlement completedSettlement() {
        return PayoutSettlement.builder()
                .withdrawalRequestId(request.getId())
                .teacherId(request.getTeacherId())
                .ownerType(WalletOwnerType.TEACHER)
                .walletId(wallet.getId())
                .amount(request.getRequestedAmount())
                .currency("VND")
                .status(PayoutStatus.SUCCEEDED)
                .providerReferenceId("BANK-123")
                .reconciliationStatus(ReconciliationStatus.MATCHED)
                .build();
    }

    private PayoutSettlement rejectedSettlement() {
        return PayoutSettlement.builder()
                .withdrawalRequestId(request.getId())
                .teacherId(request.getTeacherId())
                .ownerType(WalletOwnerType.TEACHER)
                .walletId(wallet.getId())
                .amount(request.getRequestedAmount())
                .currency("VND")
                .status(PayoutStatus.REJECTED)
                .decision("REJECTED")
                .decisionReason("Bank account data does not match")
                .reconciliationStatus(ReconciliationStatus.MATCHED)
                .build();
    }
}
