package com.manabihub.wallet.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.wallet.dto.response.StudentWalletResponse;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletPaymentReservation;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.enums.WalletReservationStatus;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.exception.WalletCaptureReconciliationException;
import com.manabihub.wallet.repository.WalletPaymentReservationRepository;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import com.manabihub.wallet.service.StudentWalletService;
import com.manabihub.systemconfig.model.CommercialPolicy;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentWalletServiceImpl implements StudentWalletService {

    private static final String TOP_UP_KEY_PREFIX = "wallet-topup:";
    private static final String PURCHASE_KEY_PREFIX = "wallet-purchase:";
    private static final String REFUND_KEY_PREFIX = "wallet-refund:";
    private static final String WITHDRAWAL_RESERVATION_KEY_PREFIX = "student-withdrawal-reserve:";
    private static final String WITHDRAWAL_COMPLETED_KEY_PREFIX = "student-withdrawal-complete:";
    private static final String WITHDRAWAL_RELEASE_KEY_PREFIX = "student-withdrawal-release:";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletPaymentReservationRepository reservationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CommercialPolicyService commercialPolicyService;

    @Override
    @Transactional(readOnly = true)
    public StudentWalletResponse getWalletOverview(UUID userId) {
        StudentProfile student = requireStudentByUserId(userId);
        final BigDecimal minimumPayoutAmount = resolveMinimumPayoutAmount();
        return walletRepository
                .findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, student.getId())
                .map(wallet -> new StudentWalletResponse(
                        wallet.getBalance(),
                        wallet.getFrozenBalance(),
                        wallet.getAvailableBalance(),
                        wallet.getWithdrawableBalance(),
                        wallet.getAvailableWithdrawableBalance(),
                        wallet.getCurrency(),
                        minimumPayoutAmount))
                .orElseGet(() -> new StudentWalletResponse(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "VND",
                        minimumPayoutAmount));
    }

    private BigDecimal resolveMinimumPayoutAmount() {
        if (commercialPolicyService == null) {
            return BigDecimal.ZERO;
        }
        CommercialPolicy policy = commercialPolicyService.getCurrentPolicy();
        return policy == null || policy.payoutThreshold() == null
                ? BigDecimal.ZERO
                : policy.payoutThreshold();
    }

    @Override
    @Transactional
    public Wallet getOrCreateStudentWallet(UUID studentId) {
        return walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, studentId)
                .orElseGet(() -> {
                    UUID candidateWalletId = UUID.randomUUID();
                    walletRepository.insertStudentWalletIfAbsent(candidateWalletId, studentId);
                    return walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, studentId)
                            .orElseThrow(() -> new IllegalStateException("Failed to find or create canonical wallet"));
                });
    }

    @Override
    @Transactional
    public WalletTransaction creditTopUp(
            UUID studentId,
            BigDecimal amount,
            UUID orderId,
            String note
    ) {
        return credit(
                studentId,
                amount,
                WalletTransactionType.TOP_UP,
                "WALLET_TOPUP",
                orderId,
                TOP_UP_KEY_PREFIX + orderId,
                note,
                false
        );
    }

    @Override
    @Transactional
    public WalletTransaction creditRefund(
            UUID studentId,
            BigDecimal amount,
            UUID refundRequestId,
            String note
    ) {
        return credit(
                studentId,
                amount,
                WalletTransactionType.REFUND,
                "REFUND_REQUEST",
                refundRequestId,
                REFUND_KEY_PREFIX + refundRequestId,
                note,
                true
        );
    }

    @Override
    @Transactional
    public WalletTransaction creditPromotionalReward(
            UUID studentId,
            BigDecimal amount,
            WalletTransactionType type,
            String referenceType,
            UUID referenceId,
            String idempotencyKey,
            String note
    ) {
        if (type != WalletTransactionType.GAME_REWARD
                && type != WalletTransactionType.ATTENDANCE_REWARD) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Unsupported promotional reward type",
                    HttpStatus.BAD_REQUEST);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Reward idempotency key is required",
                    HttpStatus.BAD_REQUEST);
        }
        return credit(studentId, amount, type, referenceType, referenceId,
                idempotencyKey, note, false);
    }

    @Override
    @Transactional
    public WalletTransaction reserveForWithdrawal(
            UUID studentId,
            UUID withdrawalId,
            BigDecimal amount
    ) {
        requirePositive(amount, "Withdrawal amount must be positive");
        String idempotencyKey = WITHDRAWAL_RESERVATION_KEY_PREFIX + withdrawalId;
        WalletTransaction existing = walletTransactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        Wallet wallet = getOrCreateStudentWallet(studentId);
        existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return existing;
        }

        int reserved = walletRepository.reserveStudentWithdrawalBalance(studentId, amount);
        if (reserved == 0) {
            wallet = walletRepository.findByOwnerTypeAndStudent_Id(
                            WalletOwnerType.STUDENT, studentId)
                    .orElseThrow(this::walletNotFound);
        }
        if (reserved == 0 && wallet.isFrozen()) {
            throw new BusinessException(
                    MessageCodes.MSG_WALLET_003,
                    "Ví doanh thu đang bị tạm khóa do vi phạm hoặc đang chờ xử lý.",
                    HttpStatus.CONFLICT);
        }
        if (reserved == 0) {
            throw new BusinessException(
                    MessageCodes.MSG_WALLET_004,
                    "Chỉ số dư hoàn tiền mới được rút. Số tiền yêu cầu vượt quá số dư có thể rút.",
                    HttpStatus.BAD_REQUEST);
        }

        return walletTransactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(WalletTransactionType.WITHDRAWAL_RESERVATION)
                .amount(amount)
                .direction(WalletDirection.OUT)
                .referenceType("WITHDRAWAL_REQUEST")
                .referenceId(withdrawalId)
                .idempotencyKey(idempotencyKey)
                .note("Reserve student refund balance for withdrawal")
                .build());
    }

    @Override
    @Transactional
    public void releaseWithdrawal(
            UUID studentId,
            UUID withdrawalId,
            BigDecimal amount,
            WalletTransactionType releaseType,
            String note
    ) {
        requirePositive(amount, "Withdrawal release amount must be positive");
        if (releaseType != WalletTransactionType.WITHDRAWAL_CANCELLED
                && releaseType != WalletTransactionType.WITHDRAWAL_REJECTED) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Unsupported withdrawal release type",
                    HttpStatus.BAD_REQUEST);
        }
        String idempotencyKey = WITHDRAWAL_RELEASE_KEY_PREFIX
                + releaseType.name().toLowerCase() + ":" + withdrawalId;
        if (walletTransactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }

        Wallet wallet = walletRepository.findByOwnerTypeAndStudent_IdForUpdate(WalletOwnerType.STUDENT, studentId)
                .orElseThrow(this::walletNotFound);
        if (walletTransactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }
        if (wallet.getFrozenBalance().compareTo(amount) < 0
                || wallet.getFrozenWithdrawableBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_INSUFFICIENT_RESERVED_BALANCE,
                    "Reserved student withdrawal balance is inconsistent",
                    HttpStatus.CONFLICT);
        }

        wallet.setFrozenBalance(wallet.getFrozenBalance().subtract(amount));
        wallet.setFrozenWithdrawableBalance(
                wallet.getFrozenWithdrawableBalance().subtract(amount));
        walletRepository.save(wallet);
        walletTransactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(releaseType)
                .amount(amount)
                .direction(WalletDirection.IN)
                .referenceType("WITHDRAWAL_REQUEST")
                .referenceId(withdrawalId)
                .idempotencyKey(idempotencyKey)
                .note(note)
                .build());
    }

    @Override
    @Transactional
    public WalletTransaction completeWithdrawal(
            UUID studentId,
            UUID withdrawalId,
            BigDecimal amount
    ) {
        requirePositive(amount, "Withdrawal completion amount must be positive");
        String idempotencyKey = WITHDRAWAL_COMPLETED_KEY_PREFIX + withdrawalId;
        WalletTransaction existing = walletTransactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        Wallet wallet = walletRepository.findByOwnerTypeAndStudent_IdForUpdate(WalletOwnerType.STUDENT, studentId)
                .orElseThrow(this::walletNotFound);
        existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return existing;
        }
        if (wallet.getBalance().compareTo(amount) < 0
                || wallet.getFrozenBalance().compareTo(amount) < 0
                || wallet.getWithdrawableBalance().compareTo(amount) < 0
                || wallet.getFrozenWithdrawableBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_INSUFFICIENT_RESERVED_BALANCE,
                    "Reserved student withdrawal balance is inconsistent",
                    HttpStatus.CONFLICT);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setFrozenBalance(wallet.getFrozenBalance().subtract(amount));
        wallet.setWithdrawableBalance(wallet.getWithdrawableBalance().subtract(amount));
        wallet.setFrozenWithdrawableBalance(
                wallet.getFrozenWithdrawableBalance().subtract(amount));
        walletRepository.save(wallet);
        return walletTransactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(WalletTransactionType.WITHDRAWAL_COMPLETED)
                .amount(amount)
                .direction(WalletDirection.OUT)
                .referenceType("WITHDRAWAL_REQUEST")
                .referenceId(withdrawalId)
                .idempotencyKey(idempotencyKey)
                .note("Student wallet withdrawal completed")
                .build());
    }

    @Override
    @Transactional
    public WalletPaymentReservation reserveForOrder(
            UUID studentId,
            UUID orderId,
            BigDecimal amount,
            Instant expiresAt
    ) {
        requirePositive(amount, "Wallet reservation amount must be positive");
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Wallet reservation expiry must be in the future",
                    HttpStatus.BAD_REQUEST);
        }

        getOrCreateStudentWallet(studentId);
        Wallet wallet = walletRepository.findByOwnerTypeAndStudent_IdForUpdate(WalletOwnerType.STUDENT, studentId)
                .orElseThrow(this::walletNotFound);

        WalletPaymentReservation existing = reservationRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (existing != null) {
            requireMatchingReservation(existing, amount);
            return existing;
        }

        if (wallet.isFrozen()) {
            throw new BusinessException(
                    MessageCodes.WALLET_FROZEN,
                    "Student wallet is frozen",
                    HttpStatus.CONFLICT);
        }
        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw insufficientBalance();
        }

        BigDecimal availableNonWithdrawable = wallet.getBalance()
                .subtract(wallet.getWithdrawableBalance())
                .subtract(wallet.getFrozenBalance()
                        .subtract(wallet.getFrozenWithdrawableBalance()));
        BigDecimal withdrawableAmount = amount.subtract(availableNonWithdrawable)
                .max(BigDecimal.ZERO);
        if (wallet.getAvailableWithdrawableBalance().compareTo(withdrawableAmount) < 0) {
            throw insufficientBalance();
        }

        wallet.setFrozenBalance(wallet.getFrozenBalance().add(amount));
        wallet.setFrozenWithdrawableBalance(
                wallet.getFrozenWithdrawableBalance().add(withdrawableAmount));
        walletRepository.save(wallet);

        return reservationRepository.save(WalletPaymentReservation.builder()
                .walletId(wallet.getId())
                .orderId(orderId)
                .amount(amount)
                .withdrawableAmount(withdrawableAmount)
                .status(WalletReservationStatus.RESERVED)
                .expiresAt(expiresAt)
                .build());
    }

    @Override
    @Transactional(noRollbackFor = WalletCaptureReconciliationException.class)
    public WalletTransaction captureForOrder(UUID orderId, Instant succeededAt) {
        String idempotencyKey = PURCHASE_KEY_PREFIX + orderId;
        WalletTransaction existingTransaction = walletTransactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existingTransaction != null) {
            return existingTransaction;
        }

        WalletPaymentReservation reservation = reservationRepository
                .findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new WalletCaptureReconciliationException(
                        "Wallet reservation was not found"));

        if (reservation.getStatus() == WalletReservationStatus.CAPTURED) {
            return walletTransactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new WalletCaptureReconciliationException(
                            "Captured reservation is missing its wallet ledger entry"));
        }
        if (reservation.getStatus() != WalletReservationStatus.RESERVED) {
            reservation.setStatus(WalletReservationStatus.RECONCILIATION_REQUIRED);
            reservationRepository.save(reservation);
            throw new WalletCaptureReconciliationException(
                    "Wallet reservation can no longer be captured");
        }

        Wallet wallet = walletRepository.findByIdForUpdate(reservation.getWalletId())
                .orElseThrow(() -> new WalletCaptureReconciliationException(
                        "Reserved wallet was not found"));
        BigDecimal amount = reservation.getAmount();
        BigDecimal withdrawableAmount = reservation.getWithdrawableAmount();
        if (wallet.getFrozenBalance().compareTo(amount) < 0
                || wallet.getBalance().compareTo(amount) < 0
                || wallet.getFrozenWithdrawableBalance().compareTo(withdrawableAmount) < 0
                || wallet.getWithdrawableBalance().compareTo(withdrawableAmount) < 0) {
            reservation.setStatus(WalletReservationStatus.RECONCILIATION_REQUIRED);
            reservationRepository.save(reservation);
            throw new WalletCaptureReconciliationException(
                    "Reserved wallet funds are inconsistent");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setFrozenBalance(wallet.getFrozenBalance().subtract(amount));
        wallet.setWithdrawableBalance(
                wallet.getWithdrawableBalance().subtract(withdrawableAmount));
        wallet.setFrozenWithdrawableBalance(
                wallet.getFrozenWithdrawableBalance().subtract(withdrawableAmount));
        walletRepository.save(wallet);

        WalletTransaction transaction = walletTransactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(WalletTransactionType.PURCHASE)
                .amount(amount)
                .direction(WalletDirection.OUT)
                .referenceType("ORDER")
                .referenceId(orderId)
                .idempotencyKey(idempotencyKey)
                .note("Capture phần thanh toán từ ví")
                .build());

        reservation.setStatus(WalletReservationStatus.CAPTURED);
        reservation.setCapturedAt(succeededAt == null ? Instant.now() : succeededAt);
        reservationRepository.save(reservation);
        return transaction;
    }

    @Override
    @Transactional
    public void releaseForOrder(UUID orderId, Instant releasedAt) {
        WalletPaymentReservation reservation = reservationRepository
                .findByOrderIdForUpdate(orderId)
                .orElse(null);
        if (reservation == null || reservation.getStatus() == WalletReservationStatus.RELEASED) {
            return;
        }
        if (reservation.getStatus() == WalletReservationStatus.CAPTURED) {
            return;
        }
        if (reservation.getStatus() == WalletReservationStatus.RECONCILIATION_REQUIRED) {
            return;
        }

        Wallet wallet = walletRepository.findByIdForUpdate(reservation.getWalletId())
                .orElseThrow(this::walletNotFound);
        if (wallet.getFrozenBalance().compareTo(reservation.getAmount()) < 0) {
            reservation.setStatus(WalletReservationStatus.RECONCILIATION_REQUIRED);
            reservationRepository.save(reservation);
            throw new BusinessException(
                    MessageCodes.COMMON_CONFLICT,
                    "Frozen wallet balance is inconsistent with the reservation",
                    HttpStatus.CONFLICT);
        }

        wallet.setFrozenBalance(wallet.getFrozenBalance().subtract(reservation.getAmount()));
        if (wallet.getFrozenWithdrawableBalance()
                .compareTo(reservation.getWithdrawableAmount()) < 0) {
            reservation.setStatus(WalletReservationStatus.RECONCILIATION_REQUIRED);
            reservationRepository.save(reservation);
            throw new BusinessException(
                    MessageCodes.COMMON_CONFLICT,
                    "Frozen withdrawable balance is inconsistent with the reservation",
                    HttpStatus.CONFLICT);
        }
        wallet.setFrozenWithdrawableBalance(
                wallet.getFrozenWithdrawableBalance()
                        .subtract(reservation.getWithdrawableAmount()));
        walletRepository.save(wallet);
        reservation.setStatus(WalletReservationStatus.RELEASED);
        reservation.setReleasedAt(releasedAt == null ? Instant.now() : releasedAt);
        reservationRepository.save(reservation);
    }

    private WalletTransaction credit(
            UUID studentId,
            BigDecimal amount,
            WalletTransactionType type,
            String referenceType,
            UUID referenceId,
            String idempotencyKey,
            String note,
            boolean withdrawable
    ) {
        requirePositive(amount, "Wallet credit amount must be positive");
        WalletTransaction existing = walletTransactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        getOrCreateStudentWallet(studentId);
        Wallet wallet = walletRepository.findByOwnerTypeAndStudent_IdForUpdate(WalletOwnerType.STUDENT, studentId)
                .orElseThrow(this::walletNotFound);

        existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return existing;
        }

        wallet.setBalance(wallet.getBalance().add(amount));
        if (withdrawable) {
            wallet.setWithdrawableBalance(wallet.getWithdrawableBalance().add(amount));
        }
        walletRepository.save(wallet);
        return walletTransactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(type)
                .amount(amount)
                .direction(WalletDirection.IN)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .idempotencyKey(idempotencyKey)
                .note(note)
                .build());
    }

    private StudentProfile requireStudentByUserId(UUID userId) {
        return studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_STUDENT_PROFILE_NOT_FOUND,
                        "Student profile was not found",
                        HttpStatus.NOT_FOUND));
    }

    private void requireMatchingReservation(
            WalletPaymentReservation reservation,
            BigDecimal amount
    ) {
        if (reservation.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(
                    MessageCodes.COMMON_CONFLICT,
                    "The order already has a reservation with a different amount",
                    HttpStatus.CONFLICT);
        }
    }

    private void requirePositive(BigDecimal amount, String message) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    message,
                    HttpStatus.BAD_REQUEST);
        }
    }

    private BusinessException walletNotFound() {
        return new BusinessException(
                MessageCodes.WALLET_NOT_FOUND,
                "Student wallet was not found",
                HttpStatus.NOT_FOUND);
    }

    private BusinessException insufficientBalance() {
        return new BusinessException(
                MessageCodes.WALLET_INSUFFICIENT_BALANCE,
                "Số dư khả dụng của ví không đủ để thanh toán",
                HttpStatus.BAD_REQUEST);
    }
}
