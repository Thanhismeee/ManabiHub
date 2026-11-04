package com.manabihub.order.enums;

/**
 * What an {@code Order} is for.
 * <ul>
 *   <li>{@code COURSE} — a course purchase (UC-08); confirmation creates enrollment + escrow.</li>
 *   <li>{@code WALLET_TOPUP} — a student wallet money top-up (MHB-37); confirmation credits the
 *       student's wallet balance, no enrollment/escrow.</li>
 * </ul>
 */
public enum OrderType {
    COURSE,
    WALLET_TOPUP
}
