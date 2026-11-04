package com.manabihub.finance.repository;

import com.manabihub.finance.dto.request.ExpenseFilterRequest;
import com.manabihub.finance.entity.SystemExpense;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class SystemExpenseSpecifications {

    private SystemExpenseSpecifications() {
    }

    public static Specification<SystemExpense> from(ExpenseFilterRequest filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null) {
                return criteriaBuilder.conjunction();
            }
            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getCategory() != null) {
                query.distinct(true);
                predicates.add(criteriaBuilder.equal(
                        root.join("lines", JoinType.INNER).get("categoryCode"),
                        filter.getCategory()
                ));
            }
            if (filter.getIncurredFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("incurredAt"),
                        filter.getIncurredFrom()
                ));
            }
            if (filter.getIncurredTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("incurredAt"),
                        filter.getIncurredTo()
                ));
            }
            if (filter.getCreatedBy() != null) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), filter.getCreatedBy()));
            }
            if (filter.getMinAmountVnd() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("totalAmountVnd"), filter.getMinAmountVnd()
                ));
            }
            if (filter.getMaxAmountVnd() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("totalAmountVnd"), filter.getMaxAmountVnd()
                ));
            }
            addLike(predicates, criteriaBuilder, root, "vendorName", filter.getVendor());
            addLike(predicates, criteriaBuilder, root, "providerCode", filter.getProviderCode());
            addLike(predicates, criteriaBuilder, root, "invoiceNumber", filter.getInvoiceNumber());
            String keyword = normalizedKeyword(filter.getKeyword());
            if (keyword != null) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("expenseCode")), keyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("vendorName")), keyword),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("providerCode"), "")),
                                keyword
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("invoiceNumber"), "")),
                                keyword
                        )
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String normalizedKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase() + "%";
    }

    private static void addLike(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            jakarta.persistence.criteria.Root<SystemExpense> root,
            String field,
            String value
    ) {
        String keyword = normalizedKeyword(value);
        if (keyword != null) {
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(criteriaBuilder.coalesce(root.<String>get(field), "")),
                    keyword
            ));
        }
    }
}
