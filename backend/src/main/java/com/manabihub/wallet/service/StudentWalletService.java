package com.manabihub.wallet.service;

import com.manabihub.wallet.dto.response.StudentWalletResponse;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletPaymentReservation;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Manages the student money wallet (MHB-37). Kept separate from teacher wallet logic.
 */
public interface StudentWalletService {

    Wallet getOrCreateStudentWallet(UUID studentId);

    /** Wallet overview (balance) for the authenticated student, resolved by their user id. */
    StudentWalletResponse getWalletOverview(UUID userId);

    /**
     * Credits {@code amount} to the student's spendable balance and records a {@code TOP_UP}
     * ledger line. Called from the payment webhook after a wallet-top-up order is confirmed.
     * Must run inside a transaction; locks the wallet row to avoid lost updates.
     */
    WalletTransaction creditTopUp(UUID studentId, BigDecimal amount,
                                  UUID orderId, String note);

    /**
     * Debits {@code amount} from the student's spendable balance (e.g. paying for a course
     * with wallet money) and records a {@code PURCHASE} ledger line. Locks the wallet row.
     *
     * @throws com.manabihub.common.exception.BusinessException with
     *         {@code WALLET_INSUFFICIENT_BALANCE} if the balance is not enough
     */
    WalletTransaction creditRefund(UUID studentId, BigDecimal amount,
                                   UUID refundRequestId, String note);

    /** Credits non-withdrawable promotional money with a caller-owned idempotency key. */
    WalletTransaction creditPromotionalReward(UUID studentId, BigDecimal amount,
                                              WalletTransactionType type, String referenceType,
                                              UUID referenceId, String idempotencyKey, String note);

    WalletTransaction reserveForWithdrawal(UUID studentId, UUID withdrawalId,
                                           BigDecimal amount);

    void releaseWithdrawal(UUID studentId, UUID withdrawalId, BigDecimal amount,
                           WalletTransactionType releaseType, String note);

    WalletTransaction completeWithdrawal(UUID studentId, UUID withdrawalId,
                                         BigDecimal amount);

    WalletPaymentReservation reserveForOrder(UUID studentId, UUID orderId,
                                             BigDecimal amount, Instant expiresAt);

    WalletTransaction captureForOrder(UUID orderId, Instant succeededAt);

    void releaseForOrder(UUID orderId, Instant releasedAt);
}
