package com.manabihub.wallet.dto.response;

import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Detail of a single wallet transaction (UC-17, alternative flow 6a).
 * <p>
 * The {@code related*} fields carry the linked order / refund / escrow / payout record when
 * the caller is permitted to see it; they are {@code null} when the reference cannot be
 * resolved or is not visible to the caller.
 */
public record WalletTransactionDetailResponse(
        UUID id,
        WalletTransactionType transactionType,
        WalletDirection direction,
        BigDecimal amount,
        String currency,
        String referenceType,
        UUID referenceId,
        String referenceCode,
        String note,
        LocalDateTime createdAt,
        RelatedRecord relatedRecord
) {

    /**
     * Minimal projection of the record this transaction points at.
     *
     * @param kind        {@code ORDER}, {@code WALLET_TOPUP}, {@code ESCROW} or {@code WITHDRAWAL_REQUEST}
     * @param id          identifier of the related record
     * @param code        human-readable code (order code, short withdrawal id, …)
     * @param status      current status of the related record
     * @param title       course / order title when applicable
     * @param amount      amount of the related record
     * @param occurredAt  creation time of the related record
     */
    public record RelatedRecord(
            String kind,
            UUID id,
            String code,
            String status,
            String title,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) {
    }
}
