package com.manabihub.wallet.repository;

import com.manabihub.wallet.dto.request.WalletTransactionFilterRequest;
import com.manabihub.wallet.entity.WalletTransaction;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Criteria builders for the wallet transaction history filters (UC-17 step 6).
 * <p>
 * Specifications are used instead of a single JPQL query because every filter is optional;
 * a {@code :param IS NULL OR …} query with an {@code IN} clause is fragile on Hibernate 6.
 */
public final class WalletTransactionSpecifications {

    private WalletTransactionSpecifications() {
    }

    /**
     * Builds the full predicate for one wallet.
     *
     * @param walletId          the caller's own wallet — always applied, never optional
     * @param filter            user-supplied filters (may be {@code null})
     * @param referenceIdMatches reference ids resolved from a free-text reference-code search;
     *                          {@code null} means the search was not requested, an empty
     *                          collection means the search matched nothing
     */
    public static Specification<WalletTransaction> forWallet(UUID walletId,
                                                             WalletTransactionFilterRequest filter,
                                                             Collection<UUID> referenceIdMatches) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("walletId"), walletId));

            if (filter != null) {
                if (filter.hasTypes()) {
                    predicates.add(root.get("transactionType").in(filter.types()));
                }
                if (filter.direction() != null) {
                    predicates.add(cb.equal(root.get("direction"), filter.direction()));
                }
                // Typed path so the comparison overloads resolve against LocalDateTime.
                Path<LocalDateTime> createdAt = root.get("createdAt");
                if (filter.fromDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(
                            createdAt, filter.fromDate().atStartOfDay()));
                }
                if (filter.toDate() != null) {
                    // Exclusive upper bound at the next midnight so the whole toDate day is included.
                    predicates.add(cb.lessThan(
                            createdAt, filter.toDate().plusDays(1).atStartOfDay()));
                }
            }

            if (referenceIdMatches != null) {
                if (referenceIdMatches.isEmpty()) {
                    // Search term supplied but nothing matched — return no rows.
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("referenceId").in(referenceIdMatches));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
