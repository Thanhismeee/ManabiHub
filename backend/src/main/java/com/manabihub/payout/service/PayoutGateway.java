package com.manabihub.payout.service;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

public interface PayoutGateway {

    String providerName();

    PayoutGatewayResult transfer(PayoutGatewayCommand command);

    PayoutGatewayResult getTransferStatus(String providerReference);

    @Data
    @Builder
    class PayoutGatewayCommand {
        private UUID settlementId;
        private BigDecimal amount;
        private String currency;
        private String bankName;
        private String bankBranch;
        private String accountHolderName;
        private String accountNumber;
        private String idempotencyKey;
        private String description;
    }

    @Data
    @Builder
    class PayoutGatewayResult {
        private boolean success;
        private String providerReference;
        private String errorCode;
        private String errorMessage;
        private boolean isRetryable;
    }
}
