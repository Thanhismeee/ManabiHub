package com.manabihub.wallet.dto.response;

import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One line of the wallet transaction history (UC-17).
 *
 * @param referenceCode human-readable reference (order code, withdrawal id, …) when resolvable
 */
public record WalletTransactionResponse(
        UUID id,
        WalletTransactionType transactionType,
        WalletDirection direction,
        BigDecimal amount,
        String currency,
        String referenceType,
        UUID referenceId,
        String referenceCode,
        String note,
        LocalDateTime createdAt
) {
}
