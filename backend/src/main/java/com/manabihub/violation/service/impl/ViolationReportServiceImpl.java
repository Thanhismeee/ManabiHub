package com.manabihub.violation.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.moderation.entity.ViolationEvidence;
import com.manabihub.moderation.repository.ViolationEvidenceRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.review.entity.CourseReview;
import com.manabihub.violation.dto.ViolationReportRequest;
import com.manabihub.violation.dto.ViolationReportResponse;
import com.manabihub.violation.entity.ViolationReport;
import com.manabihub.violation.enums.ViolationStatus;
import com.manabihub.violation.enums.ViolationTargetType;
import com.manabihub.violation.mapper.ViolationReportMapper;
import com.manabihub.violation.repository.ViolationReportRepository;
import com.manabihub.violation.service.ViolationReportService;
import com.manabihub.violation.service.ViolationEvidenceStorageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ViolationReportServiceImpl implements ViolationReportService {

    private static final int MAX_EVIDENCE_FILES = 3;

    private final ViolationReportRepository violationReportRepository;
    private final ViolationReportMapper violationReportMapper;
    private final NotificationService notificationService;
    private final EntityManager entityManager;
    private final ViolationEvidenceRepository evidenceRepository;
    private final ViolationEvidenceStorageService evidenceStorageService;

    @Value("${manabihub.violation.spam-window-minutes:60}")
    private int spamWindowMinutes;

    @Override
    @Transactional
    public ViolationReportResponse submitReport(ViolationReportRequest request, UUID reporterId) {
        return submitReport(request, List.of(), reporterId);
    }

    @Override
    @Transactional
    public ViolationReportResponse submitReport(
            ViolationReportRequest request,
            List<MultipartFile> evidenceFiles,
            UUID reporterId
    ) {
        if (request.getTargetType() != ViolationTargetType.COURSE && request.getTargetType() != ViolationTargetType.LESSON_BLOCK) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST, "Only COURSE and LESSON_BLOCK targets can be reported via this API.");
        }

        List<MultipartFile> files = evidenceFiles == null ? List.of() : evidenceFiles.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (files.size() > MAX_EVIDENCE_FILES) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Mỗi báo cáo chỉ được đính kèm tối đa 3 tệp."
            );
        }

        boolean targetExists = checkTargetExists(request.getTargetType(), request.getTargetId());
        if (!targetExists) {
            throw new BusinessException(MessageCodes.COMMON_NOT_FOUND, "Target not found");
        }

        // Lock the user row to prevent concurrent duplicate/spam submissions
        AppUser reporter = entityManager.find(AppUser.class, reporterId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (reporter == null) {
            throw new BusinessException(MessageCodes.AUTH_UNAUTHORIZED, "Reporter not found");
        }

        Instant timeWindowStart = Instant.now().minus(spamWindowMinutes, ChronoUnit.MINUTES);
        boolean isDuplicate = violationReportRepository.isDuplicateReport(
                reporterId,
                request.getTargetType(),
                request.getTargetId(),
                timeWindowStart);

        if (isDuplicate) {
            throw new BusinessException(MessageCodes.MSG_REP_002, "Duplicate report detected");
        }

        ViolationReport report = ViolationReport.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason().trim())
                .description(request.getDescription().trim())
                .status(ViolationStatus.PENDING_REVIEW)
                .build();

        report = violationReportRepository.save(report);
        saveEvidence(report.getId(), reporter, files);

        // Course Manager owns the standard moderation queue and VIOLATION_RESOLVE permission.
        notificationService.createNotificationForAdminRole(
                "COURSE_MANAGER",
                "Có báo cáo vi phạm mới",
                "Một báo cáo mới đã được gửi cho " + targetTypeLabel(request.getTargetType())
                        + " có mã " + request.getTargetId() + ".",
                NotificationTypes.VIOLATION_REPORT,
                "/admin/violations/" + report.getId()
        );

        return violationReportMapper.toResponse(report);
    }

    private void saveEvidence(UUID reportId, AppUser reporter, List<MultipartFile> files) {
        if (files.isEmpty()) {
            return;
        }

        com.manabihub.moderation.entity.ViolationReport moderationReport = entityManager.getReference(
                com.manabihub.moderation.entity.ViolationReport.class,
                reportId
        );
        for (MultipartFile file : files) {
            ViolationEvidenceStorageService.StoredEvidence stored = evidenceStorageService.store(reportId, file);
            registerRollbackCleanup(stored.storageKey());
            evidenceRepository.save(ViolationEvidence.builder()
                    .violationReport(moderationReport)
                    .evidenceType(stored.evidenceType())
                    .displayName(stored.originalName())
                    .externalUrl(evidenceStorageService.toStoredReference(stored.storageKey()))
                    .contentType(stored.contentType())
                    .submittedBy(reporter)
                    .build());
        }
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    evidenceStorageService.deleteQuietly(storageKey);
                }
            }
        });
    }

    private String targetTypeLabel(ViolationTargetType targetType) {
        return switch (targetType) {
            case COURSE -> "khóa học";
            case LESSON -> "bài học";
            case LESSON_BLOCK -> "nội dung bài học";
            case REVIEW -> "bài đánh giá";
            case USER -> "tài khoản người dùng";
        };
    }

    private boolean checkTargetExists(ViolationTargetType type, UUID targetId) {
        Class<?> entityClass = switch (type) {
            case COURSE -> Course.class;
            case LESSON, LESSON_BLOCK -> LessonBlock.class;
            case REVIEW -> CourseReview.class;
            case USER -> AppUser.class;
        };
        return entityManager.find(entityClass, targetId) != null;
    }

    private String getTargetLabel(ViolationTargetType type) {
        return switch (type) {
            case COURSE -> "khóa học";
            case LESSON, LESSON_BLOCK -> "nội dung bài học";
            case REVIEW -> "bài đánh giá";
            case USER -> "tài khoản người dùng";
        };
    }
}
