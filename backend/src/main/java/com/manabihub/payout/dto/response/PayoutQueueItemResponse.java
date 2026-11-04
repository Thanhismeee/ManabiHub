package com.manabihub.payout.dto.response;

import com.manabihub.payout.enums.PayoutStatus;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.wallet.enums.WalletOwnerType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class PayoutQueueItemResponse {
    UUID withdrawalRequestId;
    UUID walletId;
    WalletOwnerType ownerType;
    UUID ownerId;
    String ownerName;
    UUID teacherId;
    String teacherName;
    BigDecimal requestedAmount;
    String bankName;
    String accountNumberMasked;
    WithdrawalStatus status;
    PayoutStatus settlementStatus;
    ReconciliationStatus reconciliationStatus;
    LocalDateTime requestedAt;
    Instant processingStartedAt;
    String provider;
    String providerReference;
    UUID decidedBy;
    LocalDateTime decidedAt;
    LocalDateTime updatedAt;
    Instant settlementUpdatedAt;
    int retryCount;
}
