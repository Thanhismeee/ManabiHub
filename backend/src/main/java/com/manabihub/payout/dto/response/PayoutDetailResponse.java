package com.manabihub.payout.dto.response;

import com.manabihub.payout.enums.PayoutStatus;
import com.manabihub.payout.enums.PayoutNotificationStatus;
import com.manabihub.payout.enums.PayoutTransferMethod;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.wallet.enums.WalletOwnerType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PayoutDetailResponse {
    UUID withdrawalRequestId;
    UUID settlementId;
    WalletOwnerType ownerType;
    UUID ownerId;
    String ownerName;
    String ownerAccountStatus;
    UUID teacherId;
    String teacherName;
    String teacherAccountStatus;
    BigDecimal requestedAmount;
    BigDecimal availableBalance;
    BigDecimal reservedBalance;
    BigDecimal pendingClearing;
    boolean walletFrozen;
    String escrowStatus;
    WithdrawalStatus status;
    PayoutStatus settlementStatus;
    ReconciliationStatus reconciliationStatus;
    List<ReconciliationAlertResponse> reconciliationAlerts;
    String bankName;
    String bankBranch;
    String accountHolderName;
    String accountNumberMasked;
    LocalDateTime requestedAt;
    Instant processingStartedAt;
    Instant settledAt;
    String decision;
    String decisionReason;
    String gatewayProvider;
    String gatewayReference;
    PayoutTransferMethod transferMethod;
    boolean manualProofAvailable;
    boolean bankQrAvailable;
    String manualProofOriginalName;
    Long manualProofSize;
    Instant manualTransferredAt;
    String failureCode;
    String failureMessage;
    int retryCount;
    PayoutNotificationStatus notificationStatus;
    int notificationAttempts;
    List<ReconciliationHistoryResponse> reconciliationHistory;
}
