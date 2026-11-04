package com.manabihub.refund.gateway;

public interface RefundGateway {

    String provider();

    RefundGatewayResult refund(RefundGatewayCommand command);
}
