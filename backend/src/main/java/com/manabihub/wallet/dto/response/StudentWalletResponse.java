package com.manabihub.wallet.dto.response;

import java.math.BigDecimal;

/**
 * Student money-wallet overview (MHB-37).
 */
public record StudentWalletResponse(
        BigDecimal balance,
        BigDecimal frozenBalance,
        BigDecimal availableBalance,
        BigDecimal withdrawableBalance,
        BigDecimal availableWithdrawableBalance,
        String currency,
        BigDecimal minimumPayoutAmount
) {
    public StudentWalletResponse(
            BigDecimal balance,
            BigDecimal frozenBalance,
            BigDecimal availableBalance,
            String currency
    ) {
        this(
                balance,
                frozenBalance,
                availableBalance,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                currency,
                BigDecimal.ZERO
        );
    }

    public StudentWalletResponse(
            BigDecimal balance,
            BigDecimal frozenBalance,
            BigDecimal availableBalance,
            BigDecimal withdrawableBalance,
            BigDecimal availableWithdrawableBalance,
            String currency
    ) {
        this(balance, frozenBalance, availableBalance, withdrawableBalance,
                availableWithdrawableBalance, currency, BigDecimal.ZERO);
    }
}
