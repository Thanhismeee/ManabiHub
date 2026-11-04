package com.manabihub.payment.enums;

/**
 * Status of a single {@code PaymentTransaction} with a payment provider.
 * <p>
 * Mirrors the {@code chk_payment_transactions_status} check constraint.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}
