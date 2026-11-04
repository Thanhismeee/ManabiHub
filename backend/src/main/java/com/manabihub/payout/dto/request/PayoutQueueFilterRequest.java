package com.manabihub.payout.dto.request;

import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.payout.enums.PayoutStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class PayoutQueueFilterRequest {

    private UUID payoutId;
    private UUID walletId;
    private WithdrawalStatus status;
    private PayoutStatus settlementStatus;
    private ReconciliationStatus reconciliationStatus;
    private String teacherKeyword;
    private String provider;
    private String providerReference;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime requestedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime requestedTo;
}
