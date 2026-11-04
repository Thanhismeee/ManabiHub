package com.manabihub.order.enums;

/**
 * Lifecycle status of a purchase {@code Order}.
 * <p>
 * Mirrors the {@code chk_orders_status} check constraint on the {@code orders} table.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,
    CANCELLED
}
