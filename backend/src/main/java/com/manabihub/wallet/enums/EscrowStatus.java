package com.manabihub.wallet.enums;

/**
 * Status of an {@code EscrowLedger} entry holding funds for a teacher.
 * <p>
 * Mirrors the {@code chk_escrow_ledger_status} check constraint.
 */
public enum EscrowStatus {
    HELD,
    RELEASED,
    REFUNDED,
    FROZEN
}
