package com.manabihub.wallet.enums;

/**
 * Business reason behind a {@code WalletTransaction} ledger entry.
 * <p>
 * Mirrors the {@code chk_wallet_tx_type} check constraint.
 */
public enum WalletTransactionType {
    PURCHASE,
    REFUND,
    REVENUE_SHARE,
    PAYOUT,
    ADJUSTMENT,
    ESCROW_HOLD,
    ESCROW_RELEASE,
    REVENUE_CREDITED,
    REVENUE_CLEARED,
    WITHDRAWAL_RESERVATION,
    WITHDRAWAL_COMPLETED,
    WITHDRAWAL_REJECTED,
    WITHDRAWAL_CANCELLED,
    ADMIN_ADJUSTMENT,
    TOP_UP,
    GAME_REWARD,
    ATTENDANCE_REWARD
}
