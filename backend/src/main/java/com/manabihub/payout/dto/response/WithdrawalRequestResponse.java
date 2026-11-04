package com.manabihub.payout.dto.response;

import com.manabihub.payout.enums.WithdrawalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestResponse {
    private String id;
    private BigDecimal requestedAmount;
    private String currency;
    private WithdrawalStatus status;
    private String bankCode;
    private String bankName;
    private String accountHolderName;
    private String accountNumberMasked;
    private String branch;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
}
