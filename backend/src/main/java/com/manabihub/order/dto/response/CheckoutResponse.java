package com.manabihub.order.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Returned by the checkout endpoint: the created order plus the provider payment URL
 * the browser must be redirected to in order to pay.
 */
public record CheckoutResponse(
        UUID orderId,
        String orderCode,
        BigDecimal amount,
        String currency,
        String status,
        String paymentUrl
) {
}
