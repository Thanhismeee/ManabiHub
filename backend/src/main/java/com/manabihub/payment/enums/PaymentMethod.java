package com.manabihub.payment.enums;

/**
 * How the student chooses to pay for a course order.
 * <ul>
 *   <li>{@code VNPAY} — redirect to the VNPay gateway (confirmed by IPN).</li>
 *   <li>{@code WALLET} — pay instantly from the student's wallet balance (no gateway).</li>
 * </ul>
 */
public enum PaymentMethod {
    VNPAY,
    WALLET,
    /** Combined: pay as much as possible from wallet, charge the remainder via VNPay. */
    WALLET_VNPAY
}
