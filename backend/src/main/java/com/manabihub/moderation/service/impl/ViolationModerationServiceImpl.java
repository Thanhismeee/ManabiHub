package com.manabihub.moderation.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.IdentityTeacherProfileRepository;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.moderation.dto.request.ResolveViolationRequest;
import com.manabihub.moderation.dto.response.ViolationDetailResponse;
import com.manabihub.moderation.dto.response.ViolationEvidenceResponse;
import com.manabihub.moderation.dto.response.ViolationQueueItemResponse;
import com.manabihub.moderation.entity.ModerationActionRecord;
import com.manabihub.moderation.entity.ModerationDecision;
import com.manabihub.moderation.entity.ViolationEvidence;
import com.manabihub.moderation.entity.ViolationReport;
import com.manabihub.moderation.enums.ModerationActionType;
import com.manabihub.moderation.enums.ModerationDecisionType;
import com.manabihub.moderation.enums.EvidenceRequestedFrom;
import com.manabihub.moderation.enums.ViolationReportStatus;
import com.manabihub.moderation.event.ModerationNotificationEvent;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.moderation.repository.ModerationActionRecordRepository;
import com.manabihub.moderation.repository.ModerationDecisionRepository;
import com.manabihub.moderation.repository.ViolationEvidenceRepository;
import com.manabihub.moderation.repository.ViolationReportRepository;
import com.manabihub.moderation.service.ViolationModerationService;
import com.manabihub.order.repository.OrderItemRepository;
import com.manabihub.review.entity.CourseReview;
import com.manabihub.review.enums.CourseReviewStatus;
import com.manabihub.review.repository.CourseReviewRepository;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.violation.service.ViolationEvidenceStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ViolationModerationServiceImpl implements ViolationModerationService {

    private static final String PERMISSION_RESOLVE = "VIOLATION_RESOLVE";
    private static final String PERMISSION_CONTENT = "VIOLATION_CONTENT_ENFORCE";
    private static final String PERMISSION_SEVERE = "VIOLATION_SEVERE_ENFORCE";

    private static final Set<ViolationReportStatus> TERMINAL_STATUSES = Set.of(
            ViolationReportStatus.RESOLVED_NO_VIOLATION,
            ViolationReportStatus.RESOLVED_UPHELD,
            ViolationReportStatus.INVALID,
            ViolationReportStatus.CANCELLED
    );

    private final ViolationReportRepository reportRepository;
    private final ViolationEvidenceRepository evidenceRepository;
    private final ModerationDecisionRepository decisionRepository;
    private final ModerationActionRecordRepository actionRecordRepository;
    private final InternalAdminAccountRepository adminRepository;
    private final CourseRepository courseRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final AppUserRepository appUserRepository;
    private final IdentityTeacherProfileRepository teacherProfileRepository;
    private final WalletRepository walletRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final ViolationEvidenceStorageService evidenceStorageService;

    @Override
    @Transactional(readOnly = true)
    public Page<ViolationQueueItemResponse> getViolationQueue(
            ViolationReportStatus status,
            Pageable pageable,
            UUID adminId
    ) {
        requirePermission(adminId, PERMISSION_RESOLVE, MessageCodes.ADMIN_PERMISSION_DENIED);

        Page<ViolationReport> reports = status == null
                ? reportRepository.findAll(pageable)
                : reportRepository.findByStatus(status, pageable);

        return reports.map(report -> ViolationQueueItemResponse.builder()
                .reportId(report.getId())
                .status(report.getStatus())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .reporterName(report.getReporter() == null
                        ? "Unknown"
                        : report.getReporter().getFullName())
                .submittedAt(report.getCreatedAt())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public ViolationDetailResponse getViolationDetail(UUID reportId, UUID adminId) {
        requirePermission(adminId, PERMISSION_RESOLVE, MessageCodes.ADMIN_PERMISSION_DENIED);
        return buildViolationDetail(requireReport(reportId), adminId);
    }

    @Override
    @Transactional
    public ViolationDetailResponse resolveViolation(
            UUID reportId,
            ResolveViolationRequest request,
            UUID adminId
    ) {
        InternalAdminAccount admin = requirePermission(
                adminId,
                PERMISSION_RESOLVE,
                MessageCodes.ADMIN_PERMISSION_DENIED
        );
        validateRequest(request);

        ViolationReport report = reportRepository.findByIdLocked(reportId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.MODERATION_REPORT_NOT_FOUND,
                        "Violation report was not found",
                        HttpStatus.NOT_FOUND
                ));

        if (TERMINAL_STATUSES.contains(report.getStatus())) {
            throw new BusinessException(
                    MessageCodes.MODERATION_ALREADY_RESOLVED,
                    "This violation report has already been processed",
                    HttpStatus.CONFLICT
            );
        }

        UUID correlationId = UUID.randomUUID();
        ViolationReportStatus statusBefore = report.getStatus();
        ViolationReportStatus statusAfter = mapStatus(request.getDecision());
        TargetContext targetContext =
                request.getDecision() == ModerationDecisionType.DISMISSED
                        ? loadTargetContext(report)
                        : loadTargetContextForUpdate(report);

        ModerationDecision decision = decisionRepository.save(
                ModerationDecision.builder()
                        .violationReport(report)
                        .decidedBy(admin)
                        .decisionType(request.getDecision())
                        .reason(request.getDecisionNote().trim())
                        .statusBefore(statusBefore)
                        .statusAfter(statusAfter)
                        .correlationId(correlationId)
                        .evidenceRequestedFrom(request.getEvidenceRequestedFrom())
                        .build()
        );

        List<ModerationActionRecord> actionRecords = new ArrayList<>();
        if (request.getDecision() == ModerationDecisionType.UPHELD) {
            actionRecords.addAll(executeActions(
                    report,
                    decision,
                    targetContext,
                    request,
                    adminId
            ));
        } else {
            actionRecords.add(actionRecord(
                    decision,
                    ModerationActionType.NONE,
                    report.getTargetType(),
                    report.getTargetId(),
                    Map.of("reportStatus", statusBefore.name()),
                    Map.of("reportStatus", statusAfter.name())
            ));
        }

        actionRecordRepository.saveAll(actionRecords);
        decision.setActions(actionRecords);

        report.setStatus(statusAfter);
        reportRepository.save(report);

        Map<String, Object> auditMetadata = new LinkedHashMap<>();
        auditMetadata.put("correlationId", correlationId);
        auditMetadata.put("reportId", report.getId());
        auditMetadata.put("decision", request.getDecision().name());
        auditMetadata.put("decisionNote", request.getDecisionNote().trim());
        auditMetadata.put("statusBefore", statusBefore.name());
        auditMetadata.put("statusAfter", statusAfter.name());
        if (request.getEvidenceRequestedFrom() != null) {
            auditMetadata.put(
                    "evidenceRequestedFrom",
                    request.getEvidenceRequestedFrom().name()
            );
        }
        auditMetadata.put(
                "actions",
                actionRecords.stream().map(record -> record.getActionType().name()).toList()
        );
        auditMetadata.put(
                "actionTargetIds",
                actionRecords.stream().map(ModerationActionRecord::getTargetId).toList()
        );
        auditMetadata.put(
                "evidenceIds",
                evidenceRepository.findByViolationReport_IdOrderByCreatedAtAsc(reportId)
                        .stream()
                        .map(ViolationEvidence::getId)
                        .toList()
        );
        if (targetContext.affectedTeacherProfileId() != null) {
            auditMetadata.put(
                    "affectedTeacherProfileId",
                    targetContext.affectedTeacherProfileId()
            );
        }
        if (targetContext.affectedUser() != null) {
            auditMetadata.put("affectedUserId", targetContext.affectedUser().getId());
        }
        auditMetadata.put("permission", PERMISSION_RESOLVE);

        writeAuditLogs(
                adminId,
                admin.getRole().getCode().name(),
                request.getDecision(),
                report,
                statusBefore,
                statusAfter,
                actionRecords,
                auditMetadata
        );

        publishNotifications(report, request, targetContext, actionRecords);
        return buildViolationDetail(report, adminId);
    }

    private void writeAuditLogs(
            UUID adminId,
            String roleCode,
            ModerationDecisionType decisionType,
            ViolationReport report,
            ViolationReportStatus statusBefore,
            ViolationReportStatus statusAfter,
            List<ModerationActionRecord> actionRecords,
            Map<String, Object> decisionMetadata
    ) {
        auditLogService.logAdminAction(
                adminId,
                roleCode,
                decisionAuditAction(decisionType),
                "VIOLATION_REPORT",
                report.getId(),
                Map.of("status", statusBefore.name()),
                Map.of("status", statusAfter.name()),
                decisionMetadata
        );

        actionRecords.stream()
                .filter(record -> record.getActionType() != ModerationActionType.NONE)
                .forEach(record -> {
                    Map<String, Object> actionMetadata =
                            new LinkedHashMap<>(decisionMetadata);
                    actionMetadata.put("moderationAction", record.getActionType().name());
                    actionMetadata.put("actionRecordId", record.getId());
                    auditLogService.logAdminAction(
                            adminId,
                            roleCode,
                            enforcementAuditAction(record.getActionType()),
                            record.getTargetType(),
                            record.getTargetId(),
                            record.getBeforeValue(),
                            record.getAfterValue(),
                            actionMetadata
                    );
                });
    }

    private String decisionAuditAction(ModerationDecisionType decisionType) {
        return switch (decisionType) {
            case UPHELD -> "MODERATION_REPORT_UPHELD";
            case DISMISSED -> "MODERATION_REPORT_DISMISSED";
            case PENDING_EVIDENCE -> "MODERATION_EVIDENCE_REQUESTED";
            case CORRECTION_REQUIRED -> "MODERATION_CORRECTION_REQUIRED";
        };
    }

    private String enforcementAuditAction(ModerationActionType actionType) {
        return switch (actionType) {
            case FORCE_DRAFT -> "COURSE_FORCE_DRAFTED";
            case HIDE_COURSE -> "COURSE_HIDDEN";
            case REMOVE_CONTENT -> "CONTENT_REMOVED";
            case BAN_ACCOUNT -> "ACCOUNT_BANNED";
            case FREEZE_BALANCE -> "TEACHER_BALANCE_FROZEN";
            case NONE -> throw invalidAction("NONE does not have an enforcement audit action");
        };
    }

    private List<ModerationActionRecord> executeActions(
            ViolationReport report,
            ModerationDecision decision,
            TargetContext targetContext,
            ResolveViolationRequest request,
            UUID adminId
    ) {
        List<ModerationActionRecord> records = new ArrayList<>();
        for (ModerationActionType action : distinctActions(request.getActions())) {
            requireActionPermission(adminId, action);
            switch (action) {
                case FORCE_DRAFT, HIDE_COURSE ->
                        records.add(forceDraftCourse(decision, targetContext, action));
                case REMOVE_CONTENT ->
                        records.addAll(removeContent(
                                report,
                                decision,
                                targetContext,
                                request.getTargetIds()
                        ));
                case BAN_ACCOUNT -> records.add(banAccount(decision, targetContext));
                case FREEZE_BALANCE -> records.add(freezeBalance(decision, targetContext));
                case NONE -> throw invalidAction(
                        "NONE cannot be used with an upheld violation decision"
                );
            }
        }
        return records;
    }

    private ModerationActionRecord forceDraftCourse(
            ModerationDecision decision,
            TargetContext context,
            ModerationActionType action
    ) {
        Course course = requireCourse(context, action);
        CourseStatus before = course.getStatus();
        course.setStatus(CourseStatus.FORCED_DRAFT);
        courseRepository.save(course);

        return actionRecord(
                decision,
                action,
                "COURSE",
                course.getId(),
                Map.of("status", before.name()),
                Map.of("status", CourseStatus.FORCED_DRAFT.name())
        );
    }

    private List<ModerationActionRecord> removeContent(
            ViolationReport report,
            ModerationDecision decision,
            TargetContext context,
            List<UUID> requestedTargetIds
    ) {
        List<UUID> targetIds = resolveRemovalTargets(report, requestedTargetIds);
        List<ModerationActionRecord> records = new ArrayList<>();
        Course affectedCourse = null;

        for (UUID targetId : targetIds) {
            if ("LESSON".equals(normalizedTargetType(report))
                    && !report.getTargetId().equals(targetId)) {
                throw invalidAction("Removal target does not match the reported lesson");
            }
            if ("REVIEW".equals(normalizedTargetType(report))
                    && !report.getTargetId().equals(targetId)) {
                throw invalidAction("Removal target does not match the reported review");
            }

            Optional<LessonBlock> lesson = lessonBlockRepository.findByIdForModeration(targetId);
            if (lesson.isPresent()) {
                Course course = lesson.get().getModule().getCourse();
                assertSameCourse(context.course(), course);
                boolean before = lesson.get().isModerationHidden();
                lesson.get().setModerationHidden(true);
                lesson.get().setModerationHiddenAt(Instant.now());
                lessonBlockRepository.save(lesson.get());
                affectedCourse = course;
                records.add(actionRecord(
                        decision,
                        ModerationActionType.REMOVE_CONTENT,
                        "LESSON",
                        lesson.get().getId(),
                        Map.of("moderationHidden", before),
                        Map.of("moderationHidden", true)
                ));
                continue;
            }

            Optional<CourseReview> review = courseReviewRepository.findByIdForModeration(targetId);
            if (review.isPresent()) {
                Course course = review.get().getEnrollment().getCourse();
                assertSameCourse(context.course(), course);
                CourseReviewStatus before = review.get().getStatus();
                review.get().setStatus(CourseReviewStatus.HIDDEN);
                courseReviewRepository.save(review.get());
                records.add(actionRecord(
                        decision,
                        ModerationActionType.REMOVE_CONTENT,
                        "REVIEW",
                        review.get().getId(),
                        Map.of("status", before.name()),
                        Map.of("status", CourseReviewStatus.HIDDEN.name())
                ));
                continue;
            }

            throw new BusinessException(
                    MessageCodes.MODERATION_TARGET_NOT_FOUND,
                    "The selected content target no longer exists",
                    HttpStatus.NOT_FOUND
            );
        }

        if (affectedCourse != null && requiresCourseRevalidation(affectedCourse.getStatus())) {
            affectedCourse.setStatus(CourseStatus.FORCED_DRAFT);
            courseRepository.save(affectedCourse);
        }
        return records;
    }

    private ModerationActionRecord banAccount(
            ModerationDecision decision,
            TargetContext context
    ) {
        AppUser affectedUser = context.affectedUser();
        if (affectedUser == null) {
            throw invalidAction("BAN_ACCOUNT requires an affected user account");
        }

        AppUser lockedUser = appUserRepository.findByIdForUpdate(affectedUser.getId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.MODERATION_TARGET_NOT_FOUND,
                        "The affected user account no longer exists",
                        HttpStatus.NOT_FOUND
                ));
        AccountStatus before = lockedUser.getUserStatus();
        lockedUser.setUserStatus(AccountStatus.LOCKED);
        appUserRepository.save(lockedUser);

        return actionRecord(
                decision,
                ModerationActionType.BAN_ACCOUNT,
                "USER",
                lockedUser.getId(),
                Map.of("status", before.name()),
                Map.of("status", AccountStatus.LOCKED.name())
        );
    }

    private ModerationActionRecord freezeBalance(
            ModerationDecision decision,
            TargetContext context
    ) {
        if (context.affectedTeacherProfileId() == null) {
            throw invalidAction("FREEZE_BALANCE requires an affected teacher wallet");
        }

        Wallet wallet = walletRepository
                .findByOwnerTypeAndTeacher_IdForUpdate(WalletOwnerType.TEACHER, context.affectedTeacherProfileId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.MODERATION_TARGET_NOT_FOUND,
                        "The affected teacher wallet does not exist",
                        HttpStatus.NOT_FOUND
                ));

        boolean before = wallet.isFrozen();
        wallet.setFrozen(true);
        walletRepository.save(wallet);

        Map<String, Object> beforeValue = walletSnapshot(wallet, before);
        Map<String, Object> afterValue = walletSnapshot(wallet, true);
        return actionRecord(
                decision,
                ModerationActionType.FREEZE_BALANCE,
                "WALLET",
                wallet.getId(),
                beforeValue,
                afterValue
        );
    }

    private TargetContext loadTargetContextForUpdate(ViolationReport report) {
        String targetType = normalizedTargetType(report);
        return switch (targetType) {
            case "COURSE" -> contextFromCourse(courseRepository
                    .findByIdForModeration(report.getTargetId())
                    .orElseThrow(this::targetNotFound));
            case "LESSON" -> {
                LessonBlock lesson = lessonBlockRepository
                        .findByIdForModeration(report.getTargetId())
                        .orElseThrow(this::targetNotFound);
                yield contextFromLesson(lesson);
            }
            case "REVIEW" -> {
                CourseReview review = courseReviewRepository
                        .findByIdForModeration(report.getTargetId())
                        .orElseThrow(this::targetNotFound);
                yield contextFromReview(review);
            }
            case "USER" -> {
                AppUser user = appUserRepository.findByIdForUpdate(report.getTargetId())
                        .orElseThrow(this::targetNotFound);
                TeacherProfile profile = teacherProfileRepository.findByUser_Id(user.getId())
                        .orElse(null);
                yield new TargetContext(null, null, null, profile, user, user.getFullName());
            }
            default -> throw invalidAction("Unsupported violation target type: " + targetType);
        };
    }

    private TargetContext loadTargetContext(ViolationReport report) {
        String targetType = normalizedTargetType(report);
        return switch (targetType) {
            case "COURSE" -> courseRepository.findById(report.getTargetId())
                    .map(this::contextFromCourse)
                    .orElse(TargetContext.empty());
            case "LESSON" -> lessonBlockRepository.findById(report.getTargetId())
                    .map(this::contextFromLesson)
                    .orElse(TargetContext.empty());
            case "REVIEW" -> courseReviewRepository.findById(report.getTargetId())
                    .map(this::contextFromReview)
                    .orElse(TargetContext.empty());
            case "USER" -> appUserRepository.findById(report.getTargetId())
                    .map(user -> new TargetContext(
                            null,
                            null,
                            null,
                            teacherProfileRepository.findByUser_Id(user.getId()).orElse(null),
                            user,
                            user.getFullName()
                    ))
                    .orElse(TargetContext.empty());
            default -> TargetContext.empty();
        };
    }

    private TargetContext contextFromCourse(Course course) {
        TeacherProfile profile = course.getTeacher();
        return new TargetContext(
                course,
                null,
                null,
                profile,
                resolveIdentityUser(profile),
                course.getTitle()
        );
    }

    private TargetContext contextFromLesson(LessonBlock lesson) {
        Course course = lesson.getModule().getCourse();
        TeacherProfile profile = course.getTeacher();
        return new TargetContext(
                course,
                lesson,
                null,
                profile,
                resolveIdentityUser(profile),
                lesson.getTitle()
        );
    }

    private TargetContext contextFromReview(CourseReview review) {
        Course course = review.getEnrollment().getCourse();
        AppUser reviewAuthor = review.getEnrollment().getStudent().getUser();
        return new TargetContext(
                course,
                null,
                review,
                null,
                reviewAuthor,
                "Course review"
        );
    }

    private AppUser resolveIdentityUser(TeacherProfile profile) {
        if (profile == null || profile.getUser() == null) {
            return null;
        }
        return appUserRepository.findById(profile.getUser().getId()).orElse(null);
    }

    private ViolationDetailResponse buildViolationDetail(
            ViolationReport report,
            UUID adminId
    ) {
        TargetContext context = loadTargetContext(report);
        List<ModerationDecision> decisions =
                decisionRepository.findByViolationReportIdOrderByCreatedAtDesc(report.getId());

        List<ViolationDetailResponse.ModerationHistoryItem> history = decisions.stream()
                .map(decision -> ViolationDetailResponse.ModerationHistoryItem.builder()
                        .decisionId(decision.getId())
                        .decisionType(decision.getDecisionType().name())
                        .decisionNote(decision.getReason())
                        .decidedAt(decision.getCreatedAt())
                        .decidedBy(decision.getDecidedBy() == null
                                ? "System"
                                : decision.getDecidedBy().getFullName())
                        .evidenceRequestedFrom(decision.getEvidenceRequestedFrom() == null
                                ? null
                                : decision.getEvidenceRequestedFrom().name())
                        .actions(decision.getActions() == null
                                ? List.of()
                                : decision.getActions().stream()
                                        .map(action -> action.getActionType().name())
                                        .toList())
                        .build())
                .toList();

        ViolationDetailResponse.ReporterSummary reporter = buildReporter(report.getReporter());
        ViolationDetailResponse.ViolationTarget target =
                ViolationDetailResponse.ViolationTarget.builder()
                        .targetType(report.getTargetType())
                        .targetId(report.getTargetId())
                        .courseId(context.course() == null ? null : context.course().getId())
                        .courseTitle(context.course() == null ? null : context.course().getTitle())
                        .currentStatus(resolveCurrentStatus(context))
                        .affectedTeacherProfileId(context.affectedTeacherProfileId())
                        .affectedUserId(context.affectedUser() == null
                                ? null
                                : context.affectedUser().getId())
                        .affectedUserName(context.affectedUser() == null
                                ? null
                                : context.affectedUser().getFullName())
                        .contentTitle(context.contentTitle())
                        .build();

        List<ViolationEvidenceResponse> evidence = evidenceRepository
                .findByViolationReport_IdOrderByCreatedAtAsc(report.getId())
                .stream()
                .map(this::toEvidenceResponse)
                .flatMap(Optional::stream)
                .toList();

        long paidPurchasers = context.course() == null
                ? 0
                : orderItemRepository.countPaidPurchasersByCourseId(context.course().getId());
        long priorWarnings = reportRepository.countByTargetTypeIgnoreCaseAndTargetIdAndStatus(
                report.getTargetType(),
                report.getTargetId(),
                ViolationReportStatus.RESOLVED_UPHELD
        );

        boolean canApplyContent = hasPermission(adminId, PERMISSION_CONTENT);
        boolean canApplySevere = hasPermission(adminId, PERMISSION_SEVERE)
                && context.affectedUser() != null;

        return ViolationDetailResponse.builder()
                .reportId(report.getId())
                .status(report.getStatus())
                .reason(report.getReason())
                .description(report.getDescription())
                .submittedAt(report.getCreatedAt())
                .reporter(reporter)
                .target(target)
                .evidence(evidence)
                .moderationHistory(history)
                .previousWarnings(Math.toIntExact(Math.min(priorWarnings, Integer.MAX_VALUE)))
                .paidEnrollmentCount(Math.toIntExact(Math.min(paidPurchasers, Integer.MAX_VALUE)))
                .availableActions(availableActions(context, canApplyContent, canApplySevere))
                .severeActionAllowed(canApplySevere)
                .build();
    }

    private ViolationDetailResponse.ReporterSummary buildReporter(AppUser reporter) {
        if (reporter == null) {
            return null;
        }
        String role = reporter.getRoles().stream()
                .map(value -> value.getCode().name())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("USER");
        String accountAge = reporter.getCreatedAt() == null
                ? null
                : ChronoUnit.DAYS.between(reporter.getCreatedAt(), Instant.now()) + " days";

        return ViolationDetailResponse.ReporterSummary.builder()
                .reporterId(reporter.getId())
                .displayName(reporter.getFullName())
                .role(role)
                .accountAge(accountAge)
                .build();
    }

    private Optional<ViolationEvidenceResponse> toEvidenceResponse(ViolationEvidence evidence) {
        if (isPrivateEvidence(evidence.getExternalUrl())) {
            return Optional.of(ViolationEvidenceResponse.builder()
                    .evidenceId(evidence.getId())
                    .evidenceType(evidence.getEvidenceType())
                    .displayName(evidence.getDisplayName())
                    .accessUrl("/v1/admin/violations/" + evidence.getViolationReport().getId()
                            + "/evidence/" + evidence.getId())
                    .contentType(evidence.getContentType())
                    .submittedAt(evidence.getCreatedAt())
                    .build());
        }
        String safeUrl = safeExternalUrl(evidence.getExternalUrl());
        if (safeUrl == null) {
            return Optional.empty();
        }
        return Optional.of(ViolationEvidenceResponse.builder()
                .evidenceId(evidence.getId())
                .evidenceType(evidence.getEvidenceType())
                .displayName(evidence.getDisplayName())
                .accessUrl(safeUrl)
                .contentType(evidence.getContentType())
                .submittedAt(evidence.getCreatedAt())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public ViolationEvidenceDownload getViolationEvidence(
            UUID reportId,
            UUID evidenceId,
            UUID adminId
    ) {
        requirePermission(adminId, PERMISSION_RESOLVE, MessageCodes.ADMIN_PERMISSION_DENIED);
        ViolationEvidence evidence = evidenceRepository
                .findByIdAndViolationReport_Id(evidenceId, reportId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Không tìm thấy tệp bằng chứng.",
                        HttpStatus.NOT_FOUND
                ));
        String storageKey = evidenceStorageService.parseStorageKey(evidence.getExternalUrl());
        return new ViolationEvidenceDownload(
                evidence.getDisplayName(),
                StringUtils.hasText(evidence.getContentType())
                        ? evidence.getContentType()
                        : "application/octet-stream",
                evidenceStorageService.load(storageKey)
        );
    }

    private boolean isPrivateEvidence(String value) {
        return value != null && value.startsWith(ViolationEvidenceStorageService.STORAGE_PREFIX);
    }

    private List<ModerationActionType> availableActions(
            TargetContext context,
            boolean canApplyContent,
            boolean canApplySevere
    ) {
        List<ModerationActionType> actions = new ArrayList<>();
        if (canApplyContent
                && (context.lesson() != null || context.review() != null)) {
            actions.add(ModerationActionType.REMOVE_CONTENT);
        } else if (canApplyContent && context.course() != null) {
            actions.add(ModerationActionType.FORCE_DRAFT);
            actions.add(ModerationActionType.HIDE_COURSE);
            actions.add(ModerationActionType.REMOVE_CONTENT);
        }
        if (canApplySevere) {
            actions.add(ModerationActionType.BAN_ACCOUNT);
            if (context.affectedTeacherProfileId() != null) {
                actions.add(ModerationActionType.FREEZE_BALANCE);
            }
        }
        return List.copyOf(actions);
    }

    private void validateRequest(ResolveViolationRequest request) {
        if (request == null || request.getDecision() == null) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "A moderation decision is required"
            );
        }
        if (!StringUtils.hasText(request.getDecisionNote())) {
            throw new BusinessException(
                    MessageCodes.MODERATION_DECISION_NOTE_REQUIRED,
                    "A decision note is required"
            );
        }
        if (request.getDecisionNote().trim().length() > 2000) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Decision note must not exceed 2000 characters"
            );
        }

        List<ModerationActionType> actions = request.getActions() == null
                ? List.of()
                : request.getActions();
        if (actions.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalidAction("Moderation actions cannot contain null values");
        }
        if (new LinkedHashSet<>(actions).size() != actions.size()) {
            throw invalidAction("Duplicate moderation actions are not allowed");
        }

        if (request.getDecision() == ModerationDecisionType.UPHELD) {
            if (actions.isEmpty()) {
                throw new BusinessException(
                        MessageCodes.MODERATION_ACTION_REQUIRED,
                        "At least one enforcement action is required"
                );
            }
            if (actions.contains(ModerationActionType.NONE)) {
                throw invalidAction("NONE is not an enforcement action");
            }
        } else if (!actions.isEmpty()) {
            throw invalidAction(
                    "Enforcement actions are only allowed for an upheld decision"
            );
        }

        if (request.getDecision() == ModerationDecisionType.PENDING_EVIDENCE
                && request.getEvidenceRequestedFrom() == null) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "The evidence provider is required for an evidence request"
            );
        }
        if (request.getDecision() != ModerationDecisionType.PENDING_EVIDENCE
                && request.getEvidenceRequestedFrom() != null) {
            throw invalidAction(
                    "An evidence provider is only valid for a pending-evidence decision"
            );
        }
    }

    private void requireActionPermission(UUID adminId, ModerationActionType action) {
        String permission = switch (action) {
            case FORCE_DRAFT, HIDE_COURSE, REMOVE_CONTENT -> PERMISSION_CONTENT;
            case BAN_ACCOUNT, FREEZE_BALANCE -> PERMISSION_SEVERE;
            case NONE -> PERMISSION_RESOLVE;
        };
        String messageCode = permission.equals(PERMISSION_SEVERE)
                ? MessageCodes.MODERATION_SEVERE_PERMISSION_REQUIRED
                : MessageCodes.MODERATION_CONTENT_PERMISSION_REQUIRED;
        requirePermission(adminId, permission, messageCode);
    }

    private InternalAdminAccount requirePermission(
            UUID adminId,
            String permission,
            String messageCode
    ) {
        InternalAdminAccount admin = adminRepository.findById(adminId)
                .filter(value -> value.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.ADMIN_PERMISSION_DENIED,
                        "The moderation administrator is not active",
                        HttpStatus.FORBIDDEN
                ));
        if (!adminRepository.hasPermission(adminId, permission)) {
            throw new BusinessException(
                    messageCode,
                    "The administrator does not have permission " + permission,
                    HttpStatus.FORBIDDEN
            );
        }
        return admin;
    }

    private boolean hasPermission(UUID adminId, String permission) {
        return adminRepository.hasPermission(adminId, permission);
    }

    private void publishNotifications(
            ViolationReport report,
            ResolveViolationRequest request,
            TargetContext context,
            List<ModerationActionRecord> actions
    ) {
        if (request.getDecision() == ModerationDecisionType.DISMISSED) {
            publishOutcomeNotifications(
                    report.getReporter(),
                    context == null ? null : context.affectedUser(),
                    "Violation report reviewed",
                    "The report was reviewed and the reported violation was not confirmed. "
                            + request.getDecisionNote().trim(),
                    request.getDecision()
            );
            return;
        }

        AppUser affectedUser = context == null ? null : context.affectedUser();
        if (request.getDecision() == ModerationDecisionType.PENDING_EVIDENCE) {
            publishEvidenceRequestNotifications(
                    report.getReporter(),
                    affectedUser,
                    request.getEvidenceRequestedFrom(),
                    request.getDecisionNote().trim()
            );
            return;
        }
        String title = request.getDecision() == ModerationDecisionType.CORRECTION_REQUIRED
                ? "Nội dung cần được chỉnh sửa"
                : "Báo cáo vi phạm đã được xác nhận";
        String actionNames = actions.stream()
                .map(record -> moderationActionLabel(record.getActionType()))
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("Không áp dụng biện pháp");
        String message = request.getDecision() == ModerationDecisionType.CORRECTION_REQUIRED
                ? "Nội dung bị báo cáo cần được chỉnh sửa trước khi có thể tiếp tục. Ghi chú: "
                        + request.getDecisionNote().trim()
                : "Đã xác nhận có vi phạm. Biện pháp áp dụng: "
                        + actionNames
                        + ". Lý do: "
                        + request.getDecisionNote().trim();
        publishOutcomeNotifications(
                report.getReporter(),
                affectedUser,
                title,
                message,
                request.getDecision()
        );
    }

    private void publishOutcomeNotifications(
            AppUser reporter,
            AppUser affectedUser,
            String title,
            String message,
            ModerationDecisionType decision
    ) {
        Set<UUID> notifiedUserIds = new LinkedHashSet<>();
        publishOutcomeNotification(
                reporter,
                title,
                message,
                decision,
                notifiedUserIds
        );
        publishOutcomeNotification(
                affectedUser,
                title,
                message,
                decision,
                notifiedUserIds
        );
    }

    private void publishOutcomeNotification(
            AppUser recipient,
            String title,
            String message,
            ModerationDecisionType decision,
            Set<UUID> notifiedUserIds
    ) {
        if (recipient == null || !notifiedUserIds.add(recipient.getId())) {
            return;
        }
        eventPublisher.publishEvent(new ModerationNotificationEvent(
                recipient.getId(),
                recipient.getEmail(),
                title,
                message,
                NotificationTypes.MODERATION_DECISION
        ));
    }

    private void publishEvidenceRequestNotifications(
            AppUser reporter,
            AppUser creator,
            EvidenceRequestedFrom requestedFrom,
            String decisionNote
    ) {
        if ((requestedFrom == EvidenceRequestedFrom.REPORTER
                || requestedFrom == EvidenceRequestedFrom.BOTH)
                && reporter != null) {
            publishEvidenceRequest(reporter, decisionNote);
        }
        if ((requestedFrom == EvidenceRequestedFrom.CREATOR
                || requestedFrom == EvidenceRequestedFrom.BOTH)
                && creator != null
                && (reporter == null
                    || requestedFrom != EvidenceRequestedFrom.BOTH
                    || !creator.getId().equals(reporter.getId()))) {
            publishEvidenceRequest(creator, decisionNote);
        }
    }

    private void publishEvidenceRequest(AppUser recipient, String decisionNote) {
        eventPublisher.publishEvent(new ModerationNotificationEvent(
                recipient.getId(),
                recipient.getEmail(),
                "Yêu cầu bổ sung bằng chứng",
                "Vui lòng bổ sung bằng chứng cho báo cáo vi phạm này. Ghi chú: "
                        + decisionNote,
                NotificationTypes.MODERATION_EVIDENCE_REQUIRED
        ));
    }

    private String moderationActionLabel(ModerationActionType actionType) {
        return switch (actionType) {
            case NONE -> "Không áp dụng biện pháp";
            case FORCE_DRAFT -> "Chuyển nội dung về bản nháp";
            case REMOVE_CONTENT -> "Gỡ nội dung";
            case HIDE_COURSE -> "Ẩn khóa học";
            case BAN_ACCOUNT -> "Khóa tài khoản";
            case FREEZE_BALANCE -> "Đóng băng số dư";
        };
    }

    private ViolationReport requireReport(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.MODERATION_REPORT_NOT_FOUND,
                        "Violation report was not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private List<ModerationActionType> distinctActions(
            Collection<ModerationActionType> actions
    ) {
        return List.copyOf(new LinkedHashSet<>(actions));
    }

    private List<UUID> resolveRemovalTargets(
            ViolationReport report,
            List<UUID> requestedTargetIds
    ) {
        String targetType = normalizedTargetType(report);
        if (!Set.of("COURSE", "LESSON", "REVIEW").contains(targetType)) {
            throw invalidAction(
                    "REMOVE_CONTENT requires a course, lesson, or review report"
            );
        }
        if (requestedTargetIds != null && !requestedTargetIds.isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(requestedTargetIds));
        }
        if (Set.of("LESSON", "REVIEW").contains(targetType)) {
            return List.of(report.getTargetId());
        }
        throw invalidAction(
                "REMOVE_CONTENT requires one or more lesson or review target IDs"
        );
    }

    private Course requireCourse(
            TargetContext context,
            ModerationActionType action
    ) {
        if (context == null || context.course() == null) {
            throw invalidAction(action.name() + " requires a course-related report");
        }
        return context.course();
    }

    private void assertSameCourse(Course expected, Course actual) {
        if (expected != null && !expected.getId().equals(actual.getId())) {
            throw invalidAction(
                    "The selected content does not belong to the reported course"
            );
        }
    }

    private boolean requiresCourseRevalidation(CourseStatus status) {
        return status == CourseStatus.PUBLISHED
                || status == CourseStatus.APPROVED
                || status == CourseStatus.PENDING;
    }

    private String resolveCurrentStatus(TargetContext context) {
        if (context.lesson() != null) {
            return context.lesson().isModerationHidden() ? "HIDDEN" : "VISIBLE";
        }
        if (context.review() != null) {
            return context.review().getStatus().name();
        }
        if (context.course() != null) {
            return context.course().getStatus().name();
        }
        if (context.affectedUser() != null) {
            return context.affectedUser().getUserStatus().name();
        }
        return "MISSING";
    }

    private String normalizedTargetType(ViolationReport report) {
        return report.getTargetType() == null
                ? ""
                : report.getTargetType().trim().toUpperCase(Locale.ROOT);
    }

    private ViolationReportStatus mapStatus(ModerationDecisionType decision) {
        return switch (decision) {
            case DISMISSED -> ViolationReportStatus.RESOLVED_NO_VIOLATION;
            case UPHELD -> ViolationReportStatus.RESOLVED_UPHELD;
            case PENDING_EVIDENCE -> ViolationReportStatus.PENDING_EVIDENCE;
            case CORRECTION_REQUIRED -> ViolationReportStatus.CORRECTION_REQUIRED;
        };
    }

    private ModerationActionRecord actionRecord(
            ModerationDecision decision,
            ModerationActionType action,
            String targetType,
            UUID targetId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue
    ) {
        return ModerationActionRecord.builder()
                .moderationDecision(decision)
                .actionType(action)
                .targetType(targetType)
                .targetId(targetId)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build();
    }

    private Map<String, Object> walletSnapshot(Wallet wallet, boolean frozen) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("frozen", frozen);
        snapshot.put("balance", wallet.getBalance());
        snapshot.put("reservedBalance", wallet.getFrozenBalance());
        snapshot.put("availableBalance", wallet.getAvailableBalance());
        snapshot.put("currency", wallet.getCurrency());
        return snapshot;
    }

    private String safeExternalUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (!scheme.equalsIgnoreCase("https")
                    && !scheme.equalsIgnoreCase("http"))) {
                return null;
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private BusinessException targetNotFound() {
        return new BusinessException(
                MessageCodes.MODERATION_TARGET_NOT_FOUND,
                "The reported target no longer exists",
                HttpStatus.NOT_FOUND
        );
    }

    private BusinessException invalidAction(String message) {
        return new BusinessException(
                MessageCodes.MODERATION_INVALID_ACTION,
                message,
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private record TargetContext(
            Course course,
            LessonBlock lesson,
            CourseReview review,
            TeacherProfile affectedTeacherProfile,
            AppUser affectedUser,
            String contentTitle
    ) {
        private static TargetContext empty() {
            return new TargetContext(null, null, null, null, null, null);
        }

        private UUID affectedTeacherProfileId() {
            return affectedTeacherProfile == null ? null : affectedTeacherProfile.getId();
        }
    }
}
