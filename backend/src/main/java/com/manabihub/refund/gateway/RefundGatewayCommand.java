package com.manabihub.refund.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundGatewayCommand(
        UUID refundRequestId,
        UUID orderId,
        UUID orderItemId,
        String orderCode,
        String originalProvider,
        String originalProviderTransactionId,
        String providerRequestId,
        String idempotencyKey,
        BigDecimal amount,
        String currency
) {
}
