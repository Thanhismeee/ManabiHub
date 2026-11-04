package com.manabihub.kyc.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.CertificateVerificationStatus;
import com.manabihub.kyc.domain.IdentityVerificationStatus;
import com.manabihub.kyc.domain.InternalAdminAccount;
import com.manabihub.kyc.domain.KycDocument;
import com.manabihub.kyc.domain.KycDocumentType;
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.KycRequestStatus;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.dto.request.KycReviewRequest;
import com.manabihub.kyc.dto.response.KycDocumentDownload;
import com.manabihub.kyc.dto.response.KycRequestResponse;
import com.manabihub.kyc.repository.InternalAdminAccountRepository;
import com.manabihub.kyc.repository.KycDocumentRepository;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.kyc.service.KycService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.enums.WalletOwnerType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {

    private static final UUID TEACHER_ROLE_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final List<String> REVIEW_ROLES = List.of("COURSE_MANAGER");
    private static final String RESTRICTED_DOCUMENT_PREFIX = "restricted://kyc/";

    private final KycRequestRepository kycRequestRepository;
    private final InternalAdminAccountRepository adminAccountRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final com.manabihub.kyc.repository.TeacherLanguageQualificationRepository qualificationRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final CourseRepository courseRepository;
    private final WalletRepository walletRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Value("${manabihub.kyc.storage-root:storage/kyc}")
    private String storageRoot;

    @Override
    @Transactional(readOnly = true)
    public List<KycRequestResponse> getPendingKycQueue(UUID adminId) {
        requireCourseManagerAccess(adminId);
        return kycRequestRepository.findByStatusOrderByCreatedAtDesc(KycRequestStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public KycRequestResponse getKycDetail(UUID id, UUID adminId) {
        requireCourseManagerAccess(adminId);
        return mapToResponse(kycRequestRepository.findById(id)
                .orElseThrow(() -> notFound("KYC request not found")));
    }

    @Override
    @Transactional
    public KycRequestResponse reviewKyc(UUID id, KycReviewRequest request, UUID adminId) {
        AdminContext adminContext = requireCourseManagerAccess(adminId);
        KycRequest kycRequest = kycRequestRepository.findByIdForReview(id)
                .orElseThrow(() -> notFound("KYC request not found"));
        TeacherProfile lockedTeacher = teacherProfileRepository.findForUpdateByUserId(kycRequest.getTeacherProfile().getUser().getId())
                .orElseThrow(() -> notFound("Teacher profile not found"));
        entityManager.refresh(lockedTeacher);

        KycRequestStatus currentStatus = kycRequest.getStatus();
        KycRequestStatus targetStatus = request.getStatus();
        if (lockedTeacher.getKycStatus() == TeacherKycStatus.REVOKED && targetStatus != KycRequestStatus.REVOKED) {
            throw new BusinessException(MessageCodes.COMMON_CONFLICT, "Resolve the teaching suspension before reviewing another qualification", HttpStatus.CONFLICT);
        }
        boolean isRevoke = currentStatus == KycRequestStatus.APPROVED
                && targetStatus == KycRequestStatus.REVOKED;
        if (currentStatus != KycRequestStatus.PENDING && !isRevoke) {
            throw transitionConflict(targetStatus);
        }
        if (targetStatus == KycRequestStatus.REVOKED && !isRevoke) {
            throw transitionConflict(targetStatus);
        }
        if (targetStatus != KycRequestStatus.APPROVED
                && targetStatus != KycRequestStatus.REJECTED
                && targetStatus != KycRequestStatus.CORRECTION_REQUIRED
                && targetStatus != KycRequestStatus.REVOKED) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Unsupported KYC review decision",
                    HttpStatus.BAD_REQUEST
            );
        }

        requireDecisionReason(targetStatus, request.getDecisionNote());
        if (targetStatus == KycRequestStatus.APPROVED) {
            validateManualJlptApproval(kycRequest);
        }
        if (targetStatus == KycRequestStatus.REVOKED) {
            requireConfirmedTrustCase(
                    request.getTrustCaseId(),
                    kycRequest.getTeacherProfile().getUser().getId()
            );
        }

        SuspensionImpact suspensionImpact = applyDecision(kycRequest, targetStatus);
        kycRequest.setStatus(targetStatus);
        kycRequest.setDecisionReason(request.getDecisionNote());
        kycRequest.setReviewedBy(adminId);
        kycRequest.setReviewedAt(Instant.now());
        KycRequest savedRequest = kycRequestRepository.save(kycRequest);

        writeAudit(
                savedRequest,
                currentStatus,
                targetStatus,
                request,
                adminId,
                adminContext.roleCode(),
                suspensionImpact
        );
        createResultNotification(savedRequest, targetStatus, request.getDecisionNote());
        return mapToResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public KycDocumentDownload getKycDocument(UUID requestId, UUID documentId, UUID adminId) {
        requireCourseManagerAccess(adminId);
        KycDocument document = kycDocumentRepository.findByIdAndKycRequestId(documentId, requestId)
                .orElseThrow(() -> notFound("KYC document not found"));
        String fileUrl = document.getFileUrl();
        String expectedPrefix = RESTRICTED_DOCUMENT_PREFIX + requestId + "/";
        if (fileUrl == null || !fileUrl.startsWith(expectedPrefix)) {
            throw notFound("KYC document file is unavailable");
        }

        Path root = Path.of(storageRoot).toAbsolutePath().normalize();
        Path file = root.resolve(fileUrl.substring(RESTRICTED_DOCUMENT_PREFIX.length())).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw notFound("KYC document file is unavailable");
        }
        try {
            return new KycDocumentDownload(
                    Files.readAllBytes(file),
                    document.getFileName(),
                    document.getMimeType()
            );
        } catch (IOException exception) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Could not read the secured KYC document",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    exception
            );
        }
    }

    private AdminContext requireCourseManagerAccess(UUID adminId) {
        adminAccountRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.AUTH_UNAUTHORIZED,
                        "Admin not found",
                        HttpStatus.UNAUTHORIZED
                ));
        List<String> roleCodes = adminAccountRepository.findActiveRoleCodesByAdminId(
                adminId,
                REVIEW_ROLES
        );
        if (roleCodes.isEmpty()) {
            throw new BusinessException(
                    MessageCodes.ADMIN_PERMISSION_DENIED,
                    "Access denied: Course Manager privileges required",
                    HttpStatus.FORBIDDEN
            );
        }
        return new AdminContext("COURSE_MANAGER");
    }

    private void requireDecisionReason(KycRequestStatus targetStatus, String decisionNote) {
        if ((targetStatus == KycRequestStatus.REJECTED
                || targetStatus == KycRequestStatus.CORRECTION_REQUIRED
                || targetStatus == KycRequestStatus.REVOKED)
                && (decisionNote == null || decisionNote.isBlank())) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Decision reason is required for rejection, correction, or revocation",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateManualJlptApproval(KycRequest request) {
        Map<String, Object> payload = request.getVerificationPayload();
        boolean valid = request.getIdentityStatus() == IdentityVerificationStatus.VERIFIED
                && request.getCertificateStatus() == CertificateVerificationStatus.PENDING_REVIEW
                && "CERTIFICATE".equals(payloadValue(payload, "exceptionStage"))
                && "JLPT_AUTHENTICITY_CHECK".equals(payloadValue(payload, "exceptionType"))
                && "JLPT".equals(payloadValue(payload, "certificateType"))
                && "MATCHED".equals(payloadValue(payload, "identityCrossMatch"))
                && "PASSED".equals(payloadValue(payload, "duplicateCertificateCheck"));
        if (!valid) {
            throw new BusinessException(
                    MessageCodes.COMMON_CONFLICT,
                    "Only a JLPT certificate with verified CCCD, successful OCR identity matching, "
                            + "and passed duplicate checks can be approved",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void requireConfirmedTrustCase(UUID trustCaseId, UUID teacherUserId) {
        if (trustCaseId == null) {
            throw trustCaseRequired(
                    "A resolved trust case with a BAN decision is required before revoking teacher KYC"
            );
        }
        Object result = entityManager.createNativeQuery("""
                SELECT EXISTS (
                    SELECT 1
                    FROM violation_reports report
                    JOIN moderation_decisions decision
                      ON decision.violation_report_id = report.id
                    WHERE report.id = :trustCaseId
                      AND report.target_type = 'USER'
                      AND report.target_id = :teacherUserId
                      AND report.status = 'RESOLVED'
                      AND decision.decision = 'BAN'
                )
                """)
                .setParameter("trustCaseId", trustCaseId)
                .setParameter("teacherUserId", teacherUserId)
                .getSingleResult();
        boolean confirmed = result instanceof Boolean booleanResult
                ? booleanResult
                : result instanceof Number numberResult && numberResult.intValue() == 1;
        if (!confirmed) {
            throw trustCaseRequired(
                    "The supplied trust case is not resolved with a BAN decision for this teacher"
            );
        }
    }

    private SuspensionImpact applyDecision(KycRequest request, KycRequestStatus targetStatus) {
        TeacherProfile teacher = request.getTeacherProfile();
        if (targetStatus == KycRequestStatus.APPROVED) {
            request.setCertificateStatus(CertificateVerificationStatus.APPROVED);
            teacher.setKycStatus(TeacherKycStatus.APPROVED);
            teacher.setCanPublishCourse(true);
            grantTeacherRoleIfAbsent(teacher.getUser().getId());
            teacherProfileRepository.save(teacher);
            return SuspensionImpact.none();
        }
        if (targetStatus == KycRequestStatus.REJECTED) {
            request.setCertificateStatus(CertificateVerificationStatus.REJECTED);
            if (preserveApprovedQualifications(teacher)) return SuspensionImpact.none();
            teacher.setKycStatus(TeacherKycStatus.REJECTED);
            teacher.setCanPublishCourse(false);
            revokeTeacherRole(teacher.getUser().getId());
            teacherProfileRepository.save(teacher);
            return SuspensionImpact.none();
        }
        if (targetStatus == KycRequestStatus.CORRECTION_REQUIRED) {
            request.setCertificateStatus(CertificateVerificationStatus.REJECTED);
            if (preserveApprovedQualifications(teacher)) return SuspensionImpact.none();
            teacher.setKycStatus(TeacherKycStatus.CORRECTION_REQUIRED);
            teacher.setCanPublishCourse(false);
            revokeTeacherRole(teacher.getUser().getId());
            teacherProfileRepository.save(teacher);
            return SuspensionImpact.none();
        }
        return suspendTeacherOperations(teacher);
    }

    private boolean preserveApprovedQualifications(TeacherProfile teacher) {
        if (teacher.getKycStatus() == TeacherKycStatus.REVOKED || !qualificationRepository.existsByTeacher_IdAndStatus(
                teacher.getId(), com.manabihub.kyc.domain.TeacherQualificationStatus.APPROVED)) return false;
        teacher.setKycStatus(TeacherKycStatus.APPROVED); teacher.setCanPublishCourse(true);
        grantTeacherRoleIfAbsent(teacher.getUser().getId()); teacherProfileRepository.save(teacher);
        return true;
    }

    private void writeAudit(
            KycRequest request,
            KycRequestStatus currentStatus,
            KycRequestStatus targetStatus,
            KycReviewRequest review,
            UUID adminId,
            String adminRole,
            SuspensionImpact suspensionImpact
    ) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("status", targetStatus.name());
        if (targetStatus == KycRequestStatus.REVOKED) {
            afterValue.put(
                    "coursesRemovedFromMarketplace",
                    suspensionImpact.coursesRemovedFromMarketplace()
            );
            afterValue.put("walletFrozen", suspensionImpact.walletFrozen());
            afterValue.put("existingLearnerAccessPreserved", true);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("decisionNote", review.getDecisionNote() == null ? "" : review.getDecisionNote());
        metadata.put("reviewMode", "MANUAL_JAPAN_FOUNDATION");
        if (review.getTrustCaseId() != null) {
            metadata.put("trustCaseId", review.getTrustCaseId().toString());
        }

        auditLogRepository.save(AuditLog.builder()
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(adminId)
                .actorRoleCode(adminRole)
                .action("KYC_REVIEW")
                .targetType("KYC_REQUEST")
                .targetId(request.getId())
                .beforeValue(Map.of("status", currentStatus.name()))
                .afterValue(afterValue)
                .metadata(metadata)
                .build());
    }

    private void createResultNotification(
            KycRequest request,
            KycRequestStatus targetStatus,
            String decisionNote
    ) {
        String title = "Kết quả xác minh giảng viên";
        String message = switch (targetStatus) {
            case APPROVED ->
                    "Chứng chỉ JLPT của bạn đã được xác minh. Bạn có thể gửi khóa học để xét duyệt xuất bản.";
            case REJECTED ->
                    "Chứng chỉ JLPT chưa vượt qua bước xác minh tính xác thực. Lý do: " + decisionNote;
            case CORRECTION_REQUIRED ->
                    "Hồ sơ giảng viên cần bổ sung hoặc cập nhật chứng chỉ JLPT. Nội dung cần sửa: " + decisionNote;
            case REVOKED ->
                    "Quyền giảng dạy và rút tiền đã bị tạm dừng sau khi xác nhận vi phạm. "
                            + "Học viên hiện tại vẫn được giữ quyền truy cập. Lý do: " + decisionNote;
            default -> throw new IllegalStateException("Unsupported KYC review decision");
        };

        AppUser teacherUser = request.getTeacherProfile().getUser();
        notificationService.createNotification(
                teacherUser.getId(),
                teacherUser.getEmail(),
                title,
                message,
                NotificationTypes.KYC_RESULT,
                "/teacher/kyc"
        );
    }

    private KycRequestResponse mapToResponse(KycRequest request) {
        TeacherProfile teacher = request.getTeacherProfile();
        AppUser user = teacher.getUser();
        List<KycDocument> documents =
                kycDocumentRepository.findByKycRequestIdOrderByCreatedAtAsc(request.getId());
        Map<String, Object> payload = request.getVerificationPayload();

        String processedByEmail = request.getReviewedBy() == null
                ? null
                : adminAccountRepository.findById(request.getReviewedBy())
                        .map(InternalAdminAccount::getEmail)
                        .orElse(null);

        return KycRequestResponse.builder()
                .id(request.getId())
                .teacherId(teacher.getId())
                .teacherEmail(user == null ? null : user.getEmail())
                .teacherFullName(user == null ? null : user.getFullName())
                .status(request.getStatus())
                .displayName(teacher.getDisplayName())
                .idCardFrontUrl(documentUrl(request.getId(), documents, KycDocumentType.ID_CARD_FRONT))
                .idCardBackUrl(documentUrl(request.getId(), documents, KycDocumentType.ID_CARD_BACK))
                .certificateUrl(documentUrl(request.getId(), documents, KycDocumentType.CERTIFICATE))
                .selfieUrl(documentUrl(request.getId(), documents, KycDocumentType.SELFIE))
                .copyrightAccepted(request.isCopyrightAgreed())
                .vnptVerificationStatus(payloadValue(payload, "providerStatus"))
                .vnptResponseDetails(safeVnptDetails(request))
                .riskLevel(request.getRiskLevel() == null ? null : request.getRiskLevel().name())
                .exceptionStage(payloadValue(payload, "exceptionStage"))
                .exceptionType(payloadValue(payload, "exceptionType"))
                .exceptionReason(payloadValue(payload, "exceptionReason"))
                .certificateCode(request.getCertificateCode())
                .certificateHolderName(payloadValue(payload, "certificateHolderName"))
                .certificateDateOfBirth(payloadValue(payload, "certificateDateOfBirth"))
                .certificateLevel(payloadValue(payload, "certificateLevel"))
                .certificateOcrText(payloadValue(payload, "certificateOcrText"))
                .decisionNote(request.getDecisionReason())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .processedByEmail(processedByEmail)
                .processedAt(request.getReviewedAt())
                .build();
    }

    private String safeVnptDetails(KycRequest request) {
        Map<String, Object> payload = request.getVerificationPayload();
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("provider", request.getEkycProvider());
        safe.put("providerStatus", payloadValue(payload, "providerStatus"));
        Object ocrValue = payload == null ? null : payload.get("identityOcr");
        if (ocrValue instanceof Map<?, ?> ocr) {
            Map<String, Object> safeOcr = new LinkedHashMap<>();
            safeOcr.put("fullName", nullableValue(ocr.get("fullName")));
            safeOcr.put("dateOfBirth", nullableValue(ocr.get("dateOfBirth")));
            safeOcr.put("idNumber", maskIdentityNumber(nullableValue(ocr.get("idNumber"))));
            safe.put("identityOcr", safeOcr);
        }
        Object failureReasons = payload == null ? null : payload.get("failureReasons");
        if (failureReasons != null) {
            safe.put("failureReasons", failureReasons);
        }
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String documentUrl(UUID requestId, List<KycDocument> documents, KycDocumentType type) {
        return documents.stream()
                .filter(document -> document.getDocumentType() == type)
                .map(document -> "/v1/admin/kyc-requests/" + requestId
                        + "/documents/" + document.getId())
                .findFirst()
                .orElse(null);
    }

    private String payloadValue(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) {
            return null;
        }
        return String.valueOf(payload.get(key));
    }

    private String nullableValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String maskIdentityNumber(String value) {
        if (value == null || value.length() < 6) {
            return null;
        }
        return value.substring(0, 3) + "******" + value.substring(value.length() - 3);
    }

    private void grantTeacherRoleIfAbsent(UUID userId) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM user_roles WHERE user_id = :userId AND role_id = :roleId"
        ).setParameter("userId", userId)
         .setParameter("roleId", TEACHER_ROLE_ID)
         .getSingleResult();
        if (count.longValue() == 0) {
            entityManager.createNativeQuery(
                    "INSERT INTO user_roles (user_id, role_id) VALUES (:userId, :roleId)"
            ).setParameter("userId", userId)
             .setParameter("roleId", TEACHER_ROLE_ID)
             .executeUpdate();
        }
    }

    private void revokeTeacherRole(UUID userId) {
        entityManager.createNativeQuery(
                "DELETE FROM user_roles WHERE user_id = :userId AND role_id = :roleId"
        ).setParameter("userId", userId)
         .setParameter("roleId", TEACHER_ROLE_ID)
         .executeUpdate();
    }

    private SuspensionImpact suspendTeacherOperations(TeacherProfile teacher) {
        teacher.setKycStatus(TeacherKycStatus.REVOKED);
        teacher.setCanPublishCourse(false);
        teacherProfileRepository.save(teacher);
        revokeTeacherRole(teacher.getUser().getId());

        List<Course> coursesToRemove = courseRepository
                .findByTeacher_IdAndStatusNotOrderByCreatedAtDesc(
                        teacher.getId(),
                        CourseStatus.ARCHIVED
                )
                .stream()
                .filter(course -> course.getStatus() == CourseStatus.PUBLISHED
                        || course.getStatus() == CourseStatus.APPROVED
                        || course.getStatus() == CourseStatus.PENDING)
                .toList();
        coursesToRemove.forEach(course -> course.setStatus(CourseStatus.FORCED_DRAFT));
        if (!coursesToRemove.isEmpty()) {
            courseRepository.saveAll(coursesToRemove);
        }

        boolean walletFrozen = walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(WalletOwnerType.TEACHER, teacher.getId())
                .map(this::freezeWallet)
                .orElse(false);
        return new SuspensionImpact(coursesToRemove.size(), walletFrozen);
    }

    private boolean freezeWallet(Wallet wallet) {
        wallet.setFrozen(true);
        walletRepository.save(wallet);
        return true;
    }

    private BusinessException notFound(String message) {
        return new BusinessException(MessageCodes.COMMON_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    private BusinessException transitionConflict(KycRequestStatus targetStatus) {
        return new BusinessException(
                MessageCodes.COMMON_CONFLICT,
                "KYC request cannot transition to " + targetStatus,
                HttpStatus.CONFLICT
        );
    }

    private BusinessException trustCaseRequired(String message) {
        return new BusinessException(
                MessageCodes.KYC_TRUST_CASE_REQUIRED,
                message,
                HttpStatus.CONFLICT
        );
    }

    private record AdminContext(String roleCode) {
    }

    private record SuspensionImpact(int coursesRemovedFromMarketplace, boolean walletFrozen) {
        private static SuspensionImpact none() {
            return new SuspensionImpact(0, false);
        }
    }
}
