package com.manabihub.payout.repository;

import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.payout.dto.request.PayoutQueueFilterRequest;
import com.manabihub.payout.entity.PayoutSettlement;
import com.manabihub.payout.entity.WithdrawalRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class PayoutQueueSpecification {

    private PayoutQueueSpecification() {
    }

    public static Specification<WithdrawalRequest> from(PayoutQueueFilterRequest filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getPayoutId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), filter.getPayoutId()));
            }
            if (filter.getWalletId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("walletId"), filter.getWalletId()));
            }
            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getRequestedFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("requestedAt"),
                        filter.getRequestedFrom()
                ));
            }
            if (filter.getRequestedTo() != null) {
                predicates.add(criteriaBuilder.lessThan(
                        root.get("requestedAt"),
                        filter.getRequestedTo()
                ));
            }
            if (filter.getMinAmount() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("requestedAmount"), filter.getMinAmount()
                ));
            }
            if (filter.getMaxAmount() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("requestedAmount"), filter.getMaxAmount()
                ));
            }

            String ownerKeyword = normalizeKeyword(filter.getTeacherKeyword());
            if (ownerKeyword != null) {
                predicates.add(criteriaBuilder.or(
                        teacherMatches(
                                root,
                                query.subquery(Integer.class),
                                criteriaBuilder,
                                ownerKeyword
                        ),
                        studentMatches(
                                root,
                                query.subquery(Integer.class),
                                criteriaBuilder,
                                ownerKeyword
                        )
                ));
            }
            if (filter.getReconciliationStatus() != null) {
                predicates.add(reconciliationMatches(
                        root,
                        query.subquery(Integer.class),
                        criteriaBuilder,
                        filter
                ));
            }
            if (filter.getSettlementStatus() != null
                    || normalize(filter.getProvider()) != null
                    || normalize(filter.getProviderReference()) != null) {
                predicates.add(settlementMatches(
                        root,
                        query.subquery(Integer.class),
                        criteriaBuilder,
                        filter
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate teacherMatches(
            Root<WithdrawalRequest> withdrawal,
            Subquery<Integer> subquery,
            CriteriaBuilder criteriaBuilder,
            String teacherKeyword
    ) {
        Root<TeacherProfile> teacher = subquery.from(TeacherProfile.class);
        subquery.select(criteriaBuilder.literal(1));
        subquery.where(
                criteriaBuilder.equal(teacher.get("id"), withdrawal.get("teacherId")),
                criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(criteriaBuilder.coalesce(teacher.get("displayName"), "")),
                                teacherKeyword
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(criteriaBuilder.coalesce(teacher.get("user").get("fullName"), "")),
                                teacherKeyword
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(criteriaBuilder.coalesce(teacher.get("user").get("email"), "")),
                                teacherKeyword
                        )
                )
        );
        return criteriaBuilder.exists(subquery);
    }

    private static Predicate studentMatches(
            Root<WithdrawalRequest> withdrawal,
            Subquery<Integer> subquery,
            CriteriaBuilder criteriaBuilder,
            String studentKeyword
    ) {
        Root<StudentProfile> student = subquery.from(StudentProfile.class);
        subquery.select(criteriaBuilder.literal(1));
        subquery.where(
                criteriaBuilder.equal(student.get("id"), withdrawal.get("studentId")),
                criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(criteriaBuilder.coalesce(student.get("displayName"), "")),
                                studentKeyword
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(criteriaBuilder.coalesce(student.get("user").get("fullName"), "")),
                                studentKeyword
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(criteriaBuilder.coalesce(student.get("user").get("email"), "")),
                                studentKeyword
                        )
                )
        );
        return criteriaBuilder.exists(subquery);
    }

    private static Predicate reconciliationMatches(
            Root<WithdrawalRequest> withdrawal,
            Subquery<Integer> subquery,
            CriteriaBuilder criteriaBuilder,
            PayoutQueueFilterRequest filter
    ) {
        Root<PayoutSettlement> settlement = subquery.from(PayoutSettlement.class);
        subquery.select(criteriaBuilder.literal(1));
        subquery.where(
                criteriaBuilder.equal(
                        settlement.get("withdrawalRequestId"),
                        withdrawal.get("id")
                ),
                criteriaBuilder.equal(
                        settlement.get("reconciliationStatus"),
                        filter.getReconciliationStatus()
                )
        );
        return criteriaBuilder.exists(subquery);
    }

    private static Predicate settlementMatches(
            Root<WithdrawalRequest> withdrawal,
            Subquery<Integer> subquery,
            CriteriaBuilder criteriaBuilder,
            PayoutQueueFilterRequest filter
    ) {
        Root<PayoutSettlement> settlement = subquery.from(PayoutSettlement.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(
                settlement.get("withdrawalRequestId"),
                withdrawal.get("id")
        ));
        if (filter.getSettlementStatus() != null) {
            predicates.add(criteriaBuilder.equal(settlement.get("status"), filter.getSettlementStatus()));
        }
        String provider = normalize(filter.getProvider());
        if (provider != null) {
            predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(settlement.get("provider")),
                    provider.toLowerCase()
            ));
        }
        String reference = normalizeKeyword(filter.getProviderReference());
        if (reference != null) {
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(settlement.get("providerReferenceId")),
                    reference
            ));
        }
        subquery.select(criteriaBuilder.literal(1));
        subquery.where(predicates.toArray(Predicate[]::new));
        return criteriaBuilder.exists(subquery);
    }

    private static String normalizeKeyword(String keyword) {
        String normalized = normalize(keyword);
        return normalized == null ? null : "%" + normalized.toLowerCase() + "%";
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
