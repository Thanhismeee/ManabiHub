package com.manabihub.wallet.dto.request;

import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletTransactionType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Query filters for the wallet transaction history (UC-17, normal flow step 6).
 * <p>
 * Every field is optional; omitting all of them returns the full paginated history
 * ordered by newest first.
 *
 * @param types         restrict to these transaction types (e.g. {@code TOP_UP}, {@code PURCHASE})
 * @param direction     restrict to money in ({@code IN}) or money out ({@code OUT})
 * @param fromDate      inclusive lower bound on the transaction date (wallet owner's local date)
 * @param toDate        inclusive upper bound on the transaction date
 * @param referenceCode free-text search on the related order code / reference id
 */
public record WalletTransactionFilterRequest(

        List<WalletTransactionType> types,

        WalletDirection direction,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate,

        String referenceCode
) {

    /** Normalises blank search input to {@code null} so it is treated as "no filter". */
    public String normalizedReferenceCode() {
        return referenceCode == null || referenceCode.isBlank() ? null : referenceCode.trim();
    }

    public boolean hasTypes() {
        return types != null && !types.isEmpty();
    }
}
