package com.manabihub.audit.repository;

import com.manabihub.audit.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuditLogSpecification {

    public static Specification<AuditLog> filter(
            List<UUID> actorIds,
            String internalRole,
            String targetType,
            UUID targetId,
            String action,
            Instant fromDate,
            Instant toDate
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (actorIds != null && !actorIds.isEmpty()) {
                Predicate adminIdIn = root.get("actorAdminId").in(actorIds);
                Predicate userIdIn = root.get("actorUserId").in(actorIds);
                predicates.add(criteriaBuilder.or(adminIdIn, userIdIn));
            }

            if (internalRole != null && !internalRole.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("actorRoleCode"), internalRole));
            }

            if (targetType != null && !targetType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("targetType"), targetType));
            }

            if (targetId != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetId"), targetId));
            }

            if (action != null && !action.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }

            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }

            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
