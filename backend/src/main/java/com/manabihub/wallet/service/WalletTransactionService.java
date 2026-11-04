package com.manabihub.wallet.service;

import com.manabihub.common.response.PageResponse;
import com.manabihub.wallet.dto.request.WalletTransactionFilterRequest;
import com.manabihub.wallet.dto.response.WalletTransactionDetailResponse;
import com.manabihub.wallet.dto.response.WalletTransactionResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Read-side of the wallet ledger — powers "Manage My Wallet" (UC-17) transaction history
 * and transaction detail for both Student and Teacher.
 * <p>
 * Every method resolves the wallet from the <em>authenticated user id</em>, never from a
 * client-supplied wallet id, so a caller can only ever read their own ledger (BR-RBAC-01,
 * NFR-SEC-14).
 */
public interface WalletTransactionService {

    /** Paginated, filterable history of the student's money wallet. */
    PageResponse<WalletTransactionResponse> getStudentTransactions(
            UUID userId, WalletTransactionFilterRequest filter, Pageable pageable);

    /** Paginated, filterable history of the teacher's revenue wallet. */
    PageResponse<WalletTransactionResponse> getTeacherTransactions(
            UUID userId, WalletTransactionFilterRequest filter, Pageable pageable);

    /** Detail of one student transaction, including its related order/refund reference. */
    WalletTransactionDetailResponse getStudentTransactionDetail(UUID userId, UUID transactionId);

    /** Detail of one teacher transaction, including its related escrow/payout reference. */
    WalletTransactionDetailResponse getTeacherTransactionDetail(UUID userId, UUID transactionId);
}
