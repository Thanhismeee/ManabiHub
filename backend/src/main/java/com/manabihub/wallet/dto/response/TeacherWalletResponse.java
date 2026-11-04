package com.manabihub.wallet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherWalletResponse {
    private BigDecimal pendingBalance;
    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private boolean walletFrozen;
    private BigDecimal minimumPayoutAmount;
    private int clearingPeriodDays;
    private LocalDate nextPayoutDate;
}
