package com.manabihub.oversight.service.impl;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.PageResponse;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.oversight.dto.request.DecisionReviewFilterRequest;
import com.manabihub.oversight.dto.request.DecisionWarningRequest;
import com.manabihub.oversight.dto.response.DecisionReviewDetailResponse;
import com.manabihub.oversight.dto.response.DecisionReviewSummaryResponse;
import com.manabihub.oversight.entity.OperationalDecisionReview;
import com.manabihub.oversight.enums.DecisionDomain;
import com.manabihub.oversight.enums.DecisionReviewStatus;
import com.manabihub.oversight.repository.OperationalDecisionReviewRepository;
import com.manabihub.oversight.service.OperationalDecisionReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationalDecisionReviewServiceImpl implements OperationalDecisionReviewService {

    private static final String VIEW_PERMISSION = "OPERATIONAL_DECISION_REVIEW_VIEW";
    private static final String WARN_PERMISSION = "OPERATIONAL_DECISION_WARNING_SEND";
    private static final Set<String> MANAGER_ROLES = Set.of("COURSE_MANAGER", "FINANCE_MANAGER");
    private static final Map<DecisionDomain, Set<String>> ACTIONS_BY_DOMAIN = Map.of(
            DecisionDomain.KYC, Set.of("KYC_REVIEW"),
            DecisionDomain.COURSE, Set.of(
                    "COURSE_APPROVED", "COURSE_REJECTED", "COURSE_CORRECTION_REQUESTED"
            ),
            DecisionDomain.VIOLATION, Set.of(
                    "MODERATION_REPORT_UPHELD", "MODERATION_REPORT_DISMISSED",
                    "MODERATION_EVIDENCE_REQUESTED", "MODERATION_CORRECTION_REQUIRED"
            ),
            DecisionDomain.REFUND, Set.of(
                    "APPROVE_REFUND", "APPROVE_REFUND_TO_WALLET", "REJECT_REFUND"
            ),
            DecisionDomain.PAYOUT, Set.of(
                    "PAYOUT_SETTLEMENT_SUCCEEDED", "PAYOUT_MANUAL_TRANSFER_CONFIRMED",
                    "PAYOUT_REJECTED", "PAYOUT_RECONCILIATION_BLOCKED"
            ),
            DecisionDomain.EXPENSE, Set.of(
                    "EXPENSE_CONFIRMED", "EXPENSE_PAID", "EXPENSE_VOIDED"
            )
    );
    private static final Set<String> ALL_ACTIONS = ACTIONS_BY_DOMAIN.values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toUnmodifiableSet());

    private final AuditLogRepository auditLogRepository;
    private final OperationalDecisionReviewRepository reviewRepository;
    private final InternalAdminAccountRepository adminRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DecisionReviewSummaryResponse> search(
            DecisionReviewFilterRequest filter,
            Pageable pageable
    ) {
        requirePermission(VIEW_PERMISSION);
        DecisionReviewFilterRequest effectiveFilter = filter == null
                ? new DecisionReviewFilterRequest()
                : filter;
        int page = Math.max(0, pageable == null ? 0 : pageable.getPageNumber());
        int size = Math.min(100, Math.max(1, pageable == null ? 20 : pageable.getPageSize()));

        QueryParts query = buildQuery(effectiveFilter);
        Long totalValue = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + query.fromAndWhere(),
                query.parameters(),
                Long.class
        );
        long total = totalValue == null ? 0 : totalValue;
        if (total == 0) {
            return PageResponse.<DecisionReviewSummaryResponse>builder()
                    .content(List.of()).page(page).size(size).totalElements(0).totalPages(0)
                    .first(true).last(true).build();
        }

        MapSqlParameterSource pageParameters = new MapSqlParameterSource(query.parameters().getValues())
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        List<UUID> auditIds = jdbcTemplate.queryForList(
                "SELECT log.id " + query.fromAndWhere()
                        + " ORDER BY log.created_at DESC, log.id DESC LIMIT :limit OFFSET :offset",
                pageParameters,
                UUID.class
        );
        List<DecisionReviewSummaryResponse> content = toSummaries(auditIds);
        int totalPages = (int) Math.ceil((double) total / size);
        return PageResponse.<DecisionReviewSummaryResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DecisionReviewDetailResponse get(UUID auditLogId) {
        requirePermission(VIEW_PERMISSION);
        return toDetail(requireEligibleAudit(auditLogId), reviewRepository.findByAuditLog_Id(auditLogId).orElse(null));
    }

    @Override
    @Transactional
    public DecisionReviewDetailResponse markReviewed(UUID auditLogId) {
        UUID reviewerId = requirePermission(VIEW_PERMISSION);
        AuditLog audit = requireEligibleAudit(auditLogId);
        OperationalDecisionReview review = reviewRepository.findByAuditLogIdForUpdate(auditLogId)
                .orElseGet(() -> OperationalDecisionReview.builder().auditLog(audit).build());
        if (review.getReviewStatus() != DecisionReviewStatus.WARNING_SENT) {
            review.setReviewStatus(DecisionReviewStatus.REVIEWED);
            review.setWarningLevel(null);
            review.setReviewNote(null);
            review.setWarningSentAt(null);
        }
        review.setReviewedBy(reviewerId);
        review.setReviewedAt(Instant.now());
        OperationalDecisionReview saved = reviewRepository.save(review);
        auditReviewAction(reviewerId, audit, "OPERATIONAL_DECISION_REVIEWED", Map.of());
        return toDetail(audit, saved);
    }

    @Override
    @Transactional
    public DecisionReviewDetailResponse sendWarning(UUID auditLogId, DecisionWarningRequest request) {
        UUID reviewerId = requirePermission(WARN_PERMISSION);
        AuditLog audit = requireEligibleAudit(auditLogId);
        if (audit.getActorAdminId() == null) {
            throw conflict("The original decision actor is unavailable");
        }
        InternalAdminAccount actor = adminRepository.findById(audit.getActorAdminId())
                .orElseThrow(() -> conflict("The original decision actor no longer exists"));

        OperationalDecisionReview review = reviewRepository.findByAuditLogIdForUpdate(auditLogId)
                .orElseGet(() -> OperationalDecisionReview.builder().auditLog(audit).build());
        Instant now = Instant.now();
        review.setReviewStatus(DecisionReviewStatus.WARNING_SENT);
        review.setWarningLevel(request.level());
        review.setReviewNote(request.note().trim());
        review.setReviewedBy(reviewerId);
        review.setReviewedAt(now);
        review.setWarningSentAt(now);
        OperationalDecisionReview saved = reviewRepository.save(review);

        notificationService.createAdminNotificationOnce(
                "operational-warning:" + auditLogId,
                actor.getId(),
                actor.getEmail(),
                "Cảnh báo hậu kiểm quyết định",
                "System Admin đã gửi cảnh báo mức " + request.level().name()
                        + " cho quyết định " + audit.getAction() + ". Ghi chú: " + request.note().trim(),
                NotificationTypes.OPERATIONAL_DECISION_WARNING,
                actionUrl(audit)
        );
        auditReviewAction(
                reviewerId,
                audit,
                "OPERATIONAL_DECISION_WARNING_SENT",
                Map.of("warningLevel", request.level().name(), "note", request.note().trim())
        );
        return toDetail(audit, saved);
    }

    private QueryParts buildQuery(DecisionReviewFilterRequest filter) {
        StringBuilder sql = new StringBuilder("""
                FROM audit_logs log
                JOIN internal_admin_accounts actor ON actor.id = log.actor_admin_id
                LEFT JOIN operational_decision_reviews review ON review.audit_log_id = log.id
                WHERE log.actor_role_code IN (:managerRoles)
                  AND log.action IN (:actions)
                """);
        Set<String> actions = filter.getDomain() == null
                ? ALL_ACTIONS
                : ACTIONS_BY_DOMAIN.get(filter.getDomain());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("managerRoles", MANAGER_ROLES)
                .addValue("actions", actions);

        if (filter.getDecisionRole() != null && !filter.getDecisionRole().isBlank()) {
            String role = filter.getDecisionRole().trim().toUpperCase(Locale.ROOT);
            if (!MANAGER_ROLES.contains(role)) {
                throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Unsupported decision role");
            }
            sql.append(" AND log.actor_role_code = :decisionRole");
            parameters.addValue("decisionRole", role);
        }
        if (filter.getActor() != null && !filter.getActor().isBlank()) {
            sql.append("""
                     AND (
                         LOWER(COALESCE(actor.full_name, '')) LIKE :actor
                         OR LOWER(COALESCE(actor.email, '')) LIKE :actor
                         OR CAST(actor.id AS text) = :actorId
                     )
                    """);
            String actor = filter.getActor().trim();
            parameters.addValue("actor", "%" + actor.toLowerCase(Locale.ROOT) + "%");
            parameters.addValue("actorId", actor);
        }
        if (filter.getReviewStatus() != null) {
            if (filter.getReviewStatus() == DecisionReviewStatus.UNREVIEWED) {
                sql.append(" AND review.id IS NULL");
            } else {
                sql.append(" AND review.review_status = :reviewStatus");
                parameters.addValue("reviewStatus", filter.getReviewStatus().name());
            }
        }
        if (filter.getWarningLevel() != null) {
            sql.append(" AND review.warning_level = :warningLevel");
            parameters.addValue("warningLevel", filter.getWarningLevel().name());
        }
        if (filter.getFrom() != null) {
            sql.append(" AND log.created_at >= :from");
            parameters.addValue("from", filter.getFrom());
        }
        if (filter.getTo() != null) {
            sql.append(" AND log.created_at < :to");
            parameters.addValue("to", filter.getTo());
        }
        if (filter.getFrom() != null && filter.getTo() != null
                && !filter.getFrom().isBefore(filter.getTo())) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Review range must satisfy from < to");
        }
        return new QueryParts(sql.toString(), parameters);
    }

    private List<DecisionReviewSummaryResponse> toSummaries(List<UUID> auditIds) {
        if (auditIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, AuditLog> audits = auditLogRepository.findAllById(auditIds).stream()
                .collect(Collectors.toMap(AuditLog::getId, Function.identity()));
        Map<UUID, OperationalDecisionReview> reviews = reviewRepository.findAllByAuditLog_IdIn(auditIds)
                .stream().collect(Collectors.toMap(review -> review.getAuditLog().getId(), Function.identity()));
        Set<UUID> actorIds = audits.values().stream().map(AuditLog::getActorAdminId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, InternalAdminAccount> actors = adminRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(InternalAdminAccount::getId, Function.identity()));

        Map<UUID, Integer> order = new LinkedHashMap<>();
        for (int index = 0; index < auditIds.size(); index++) {
            order.put(auditIds.get(index), index);
        }
        List<DecisionReviewSummaryResponse> result = new ArrayList<>();
        audits.values().stream().sorted(Comparator.comparingInt(audit -> order.get(audit.getId())))
                .forEach(audit -> {
                    OperationalDecisionReview review = reviews.get(audit.getId());
                    InternalAdminAccount actor = actors.get(audit.getActorAdminId());
                    result.add(new DecisionReviewSummaryResponse(
                            audit.getId(), domainFor(audit.getAction()), audit.getAction(), audit.getTargetType(),
                            audit.getTargetId(), audit.getActorAdminId(), actor == null ? null : actor.getFullName(),
                            actor == null ? null : actor.getEmail(), audit.getActorRoleCode(), audit.getCreatedAt(),
                            review == null ? DecisionReviewStatus.UNREVIEWED : review.getReviewStatus(),
                            review == null ? null : review.getWarningLevel(),
                            review == null ? null : review.getReviewedAt()
                    ));
                });
        return List.copyOf(result);
    }

    private DecisionReviewDetailResponse toDetail(AuditLog audit, OperationalDecisionReview review) {
        InternalAdminAccount actor = audit.getActorAdminId() == null
                ? null
                : adminRepository.findById(audit.getActorAdminId()).orElse(null);
        return new DecisionReviewDetailResponse(
                audit.getId(), domainFor(audit.getAction()), audit.getAction(), audit.getTargetType(),
                audit.getTargetId(), audit.getActorAdminId(), actor == null ? null : actor.getFullName(),
                actor == null ? null : actor.getEmail(), audit.getActorRoleCode(), audit.getCreatedAt(),
                nullSafeMap(audit.getBeforeValue()), nullSafeMap(audit.getAfterValue()),
                nullSafeMap(audit.getMetadata()),
                review == null ? DecisionReviewStatus.UNREVIEWED : review.getReviewStatus(),
                review == null ? null : review.getWarningLevel(),
                review == null ? null : review.getReviewNote(),
                review == null ? null : review.getReviewedBy(),
                review == null ? null : review.getReviewedAt(),
                review == null ? null : review.getWarningSentAt()
        );
    }

    private AuditLog requireEligibleAudit(UUID auditLogId) {
        AuditLog audit = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND, "Operational decision was not found", HttpStatus.NOT_FOUND));
        if (!MANAGER_ROLES.contains(audit.getActorRoleCode()) || !ALL_ACTIONS.contains(audit.getAction())) {
            throw new BusinessException(
                    MessageCodes.ADMIN_PERMISSION_DENIED,
                    "This audit event is not an operational decision eligible for review",
                    HttpStatus.FORBIDDEN
            );
        }
        return audit;
    }

    private UUID requirePermission(String permission) {
        UUID adminId = currentUserService.getCurrentUserId();
        if (!adminRepository.hasPermission(adminId, permission)) {
            throw new BusinessException(
                    MessageCodes.ADMIN_PERMISSION_DENIED,
                    "System Admin oversight permission is required",
                    HttpStatus.FORBIDDEN
            );
        }
        return adminId;
    }

    private void auditReviewAction(UUID reviewerId, AuditLog decision, String action, Map<String, Object> metadata) {
        Map<String, Object> auditMetadata = new LinkedHashMap<>(metadata);
        auditMetadata.put("decisionAuditLogId", decision.getId());
        auditMetadata.put("decisionAction", decision.getAction());
        auditLogService.logAdminAction(
                reviewerId, "SYSTEM_ADMIN", action, "AUDIT_LOG", decision.getId(),
                Map.of(), Map.of("reviewed", true), auditMetadata
        );
    }

    private DecisionDomain domainFor(String action) {
        return ACTIONS_BY_DOMAIN.entrySet().stream()
                .filter(entry -> entry.getValue().contains(action))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported operational action " + action));
    }

    private String actionUrl(AuditLog audit) {
        if (audit.getTargetId() == null) {
            return "/admin/notifications";
        }
        return switch (domainFor(audit.getAction())) {
            case KYC -> "/admin/kyc/" + audit.getTargetId();
            case COURSE -> "/admin/courses/approvals/" + audit.getTargetId();
            case VIOLATION -> "/admin/violations/" + audit.getTargetId();
            case REFUND -> "/admin/refunds/" + audit.getTargetId();
            case PAYOUT -> "/admin/payouts/" + audit.getTargetId();
            case EXPENSE -> "/admin/finance/expenses/" + audit.getTargetId();
        };
    }

    private Map<String, Object> nullSafeMap(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(MessageCodes.COMMON_CONFLICT, message, HttpStatus.CONFLICT);
    }

    private record QueryParts(String fromAndWhere, MapSqlParameterSource parameters) {
    }
}
