package com.manabihub.wallet.repository;

import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.enums.WalletDirection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, java.util.UUID>,
                JpaSpecificationExecutor<WalletTransaction> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<WalletTransaction> findByReferenceTypeAndReferenceIdAndTransactionType(
            String referenceType,
            UUID referenceId,
            WalletTransactionType transactionType
    );

    boolean existsByReferenceTypeAndReferenceIdAndTransactionType(
            String referenceType,
            UUID referenceId,
            WalletTransactionType transactionType
    );

    /**
     * Ownership-scoped lookup for the transaction detail view (UC-17 flow 6a).
     * Returning empty for a transaction that belongs to another wallet is what enforces
     * BR-RBAC-01 — the caller can never read a foreign ledger line by guessing its id.
     */
    Optional<WalletTransaction> findByIdAndWalletId(UUID id, UUID walletId);

    @Query("""
            select coalesce(sum(transaction.amount), 0)
            from WalletTransaction transaction
            where transaction.walletId = :walletId
              and transaction.transactionType = :transactionType
              and transaction.direction = :direction
            """)
    java.math.BigDecimal sumAmountByWalletIdAndTypeAndDirection(
            @Param("walletId") UUID walletId,
            @Param("transactionType") WalletTransactionType transactionType,
            @Param("direction") WalletDirection direction);
}
