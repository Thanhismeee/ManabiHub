package com.manabihub.refund.gateway;

public class UnavailableRefundGateway implements RefundGateway {

    @Override
    public String provider() {
        return "UNAVAILABLE";
    }

    @Override
    public RefundGatewayResult refund(RefundGatewayCommand command) {
        return RefundGatewayResult.unavailable("REFUND_GATEWAY_NOT_CONFIGURED");
    }
}
