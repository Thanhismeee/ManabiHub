package com.manabihub.refund.gateway;

import com.manabihub.refund.enums.RefundProviderStatus;

import java.math.BigDecimal;

public record RefundGatewayResult(
        RefundProviderStatus status,
        boolean authenticated,
        String providerReference,
        String resultCode,
        String resultMessage,
        BigDecimal refundedAmount
) {

    public static RefundGatewayResult unavailable(String code) {
        return new RefundGatewayResult(
                RefundProviderStatus.UNAVAILABLE,
                false,
                null,
                code,
                "Refund provider is unavailable",
                null
        );
    }

    public static RefundGatewayResult failed(String code) {
        return new RefundGatewayResult(
                RefundProviderStatus.FAILED,
                false,
                null,
                code,
                "Refund provider request failed",
                null
        );
    }
}
