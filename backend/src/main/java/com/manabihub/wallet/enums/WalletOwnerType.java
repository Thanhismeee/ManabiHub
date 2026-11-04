package com.manabihub.wallet.enums;

/**
 * Type of principal that owns a {@code Wallet}.
 * <p>
 * Mirrors the {@code chk_wallets_owner_type} check constraint.
 */
public enum WalletOwnerType {
    STUDENT,
    TEACHER,
    PLATFORM
}
