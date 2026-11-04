package com.manabihub.payout.dto.response;

import com.manabihub.payout.enums.PayoutStatus;
import com.manabihub.payout.enums.PayoutNotificationStatus;
import com.manabihub.payout.enums.PayoutTransferMethod;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.payout.enums.WithdrawalStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class PayoutDecisionResponse {
    UUID withdrawalRequestId;
    UUID settlementId;
    WithdrawalStatus withdrawalStatus;
    PayoutStatus settlementStatus;
    ReconciliationStatus reconciliationStatus;
    PayoutTransferMethod transferMethod;
    String gatewayReference;
    Instant settledAt;
    PayoutNotificationStatus notificationStatus;
}
