package com.manabihub.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderCode,
        BigDecimal totalAmount,
        BigDecimal walletAmount,
        String currency,
        String status,
        String type,
        Instant createdAt,
        List<OrderItemResponse> items
) {
}
