package com.manabihub.payout.service.impl;

import com.manabihub.payout.service.PayoutGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local & !test")
public class UnavailablePayoutGateway implements PayoutGateway {

    @Override
    public String providerName() {
        return "UNCONFIGURED";
    }

    @Override
    public PayoutGatewayResult transfer(PayoutGatewayCommand command) {
        return unavailable();
    }

    @Override
    public PayoutGatewayResult getTransferStatus(String providerReference) {
        return unavailable();
    }

    private PayoutGatewayResult unavailable() {
        return PayoutGatewayResult.builder()
                .success(false)
                .errorCode("PAYOUT_PROVIDER_NOT_CONFIGURED")
                .errorMessage("No payout provider is configured for this environment.")
                .isRetryable(false)
                .build();
    }
}
