package com.manabihub.refund.repository;

import com.manabihub.order.entity.OrderItemSnapshot;
import com.manabihub.payment.entity.PaymentTransaction;
import com.manabihub.refund.dto.request.RefundQueueFilterRequest;
import com.manabihub.refund.entity.RefundRequest;
import com.manabihub.refund.enums.RefundStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RefundRequestSpecifications {

    private RefundRequestSpecifications() {
    }

    public static Specification<RefundRequest> from(RefundQueueFilterRequest filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null) {
                return criteriaBuilder.conjunction();
            }
            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getRefundId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), filter.getRefundId()));
            }
            if (filter.getProviderStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("providerStatus"), filter.getProviderStatus()));
            }
            if (filter.getCreatedFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedFrom()));
            }
            if (filter.getCreatedTo() != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), filter.getCreatedTo()));
            }
            if (filter.getDecidedFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("decidedAt"), filter.getDecidedFrom()));
            }
            if (filter.getDecidedTo() != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("decidedAt"), filter.getDecidedTo()));
            }
            if (filter.getDecidedBy() != null) {
                predicates.add(criteriaBuilder.equal(root.get("decidedBy").get("id"), filter.getDecidedBy()));
            }
            if (filter.getCourseId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("orderItem").get("course").get("id"), filter.getCourseId()));
            }
            String orderCode = like(filter.getOrderCode());
            if (orderCode != null) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("order").get("orderCode")), orderCode));
            }
            String student = like(filter.getStudent());
            if (student != null) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("student").get("displayName"), "")),
                                student
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("student").get("user").get("fullName")),
                                student
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("student").get("user").get("email")),
                                student
                        )
                ));
            }
            String course = like(filter.getCourse());
            if (course != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("orderItem").get("course").get("title")),
                        course
                ));
            }
            String paymentProvider = normalized(filter.getPaymentProvider());
            if (paymentProvider != null) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<PaymentTransaction> payment = subquery.from(PaymentTransaction.class);
                subquery.select(criteriaBuilder.literal(1));
                subquery.where(
                        criteriaBuilder.equal(payment.get("order").get("id"), root.get("order").get("id")),
                        criteriaBuilder.equal(criteriaBuilder.upper(payment.get("provider")), paymentProvider.toUpperCase(Locale.ROOT))
                );
                predicates.add(criteriaBuilder.exists(subquery));
            }
            if (filter.getReconciliationRequired() != null) {
                Predicate reconciliation = criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("status"), RefundStatus.RECONCILIATION_REQUIRED),
                        criteriaBuilder.isNotNull(root.get("reconciliationReasonCode"))
                );
                predicates.add(filter.getReconciliationRequired()
                        ? reconciliation
                        : criteriaBuilder.not(reconciliation));
            }
            if (filter.getMinAmount() != null || filter.getMaxAmount() != null) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<OrderItemSnapshot> snapshot = subquery.from(OrderItemSnapshot.class);
                List<Predicate> amountPredicates = new ArrayList<>();
                amountPredicates.add(criteriaBuilder.equal(
                        snapshot.get("orderItem").get("id"),
                        root.get("orderItem").get("id")
                ));
                if (filter.getMinAmount() != null) {
                    amountPredicates.add(criteriaBuilder.greaterThanOrEqualTo(
                            snapshot.get("grossAmount"), filter.getMinAmount()));
                }
                if (filter.getMaxAmount() != null) {
                    amountPredicates.add(criteriaBuilder.lessThanOrEqualTo(
                            snapshot.get("grossAmount"), filter.getMaxAmount()));
                }
                subquery.select(criteriaBuilder.literal(1));
                subquery.where(amountPredicates.toArray(Predicate[]::new));
                predicates.add(criteriaBuilder.exists(subquery));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String like(String value) {
        String normalized = normalized(value);
        return normalized == null ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
