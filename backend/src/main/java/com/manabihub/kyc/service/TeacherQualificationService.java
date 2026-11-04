package com.manabihub.kyc.service;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.domain.CourseLanguageProfile;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.identity.service.AccountIdentityVerificationService;
import com.manabihub.kyc.domain.TeacherCredentialType;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherLanguageQualification;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.TeacherQualificationStatus;
import com.manabihub.kyc.dto.TeacherQualificationDecisionRequest;
import com.manabihub.kyc.dto.TeacherQualificationEvidenceDownload;
import com.manabihub.kyc.dto.TeacherQualificationResponse;
import com.manabihub.kyc.repository.TeacherLanguageQualificationRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherQualificationService {

    private static final long MAX_EVIDENCE_SIZE = 8L * 1024L * 1024L;
    private static final UUID TEACHER_ROLE_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf");

    private final TeacherLanguageQualificationRepository qualificationRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final AccountIdentityVerificationService accountIdentityVerificationService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;
    private final TeacherCredentialRegistry credentialRegistry;
    private final com.manabihub.kyc.repository.KycRequestRepository kycRequestRepository;

    @Value("${manabihub.kyc.storage-root:storage/kyc}")
    private String storageRoot;

    @Transactional(readOnly = true)
    public List<TeacherQualificationResponse> listMine(UUID userId) {
        return qualificationRepository.findByTeacher_User_IdOrderBySubmittedAtDesc(userId)
                .stream()
                .map(qualification -> toResponse(qualification, false))
                .toList();
    }

    @Transactional
    public TeacherQualificationResponse submit(
            UUID userId,
            LearningLanguage learningLanguage,
            ProficiencyFramework proficiencyFramework,
            String frameworkVersion,
            String proficiencyLevel,
            TeacherCredentialType credentialType,
            String credentialCode,
            String issuer,
            boolean copyrightAgreementAccepted,
            MultipartFile evidence
    ) {
        TeacherProfile teacher = teacherProfileRepository.findForUpdateByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.MSG_KYC_010,
                        "Hãy bắt đầu xác minh danh tính trước khi nộp hồ sơ chuyên môn.",
                        HttpStatus.FORBIDDEN));
        if (accountIdentityVerificationService.findVerified(userId).isEmpty()) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "Bạn cần hoàn tất xác minh danh tính trước khi nộp hồ sơ chuyên môn.",
                    HttpStatus.CONFLICT);
        }
        if (teacher.getKycStatus() == TeacherKycStatus.REVOKED) {
            throw new BusinessException(MessageCodes.MSG_KYC_010,
                    "Quyền giảng dạy đang bị thu hồi. Liên hệ quản lý để xử lý.", HttpStatus.FORBIDDEN);
        }

        CourseLanguageProfile profile = CourseLanguageProfile.resolve(
                learningLanguage,
                proficiencyFramework,
                frameworkVersion,
                proficiencyLevel,
                null);
        validateSubmission(profile, credentialType, credentialCode, issuer,
                copyrightAgreementAccepted, evidence);

        if (qualificationRepository
                .existsByTeacher_IdAndLearningLanguageAndProficiencyFrameworkAndStatus(
                        teacher.getId(), profile.learningLanguage(), profile.proficiencyFramework(),
                        TeacherQualificationStatus.PENDING)) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Hồ sơ cho ngôn ngữ và khung trình độ này đang được duyệt.",
                    HttpStatus.CONFLICT);
        }
        if (qualificationRepository
                .existsByTeacher_IdAndLearningLanguageAndProficiencyFrameworkAndStatus(
                        teacher.getId(), profile.learningLanguage(), profile.proficiencyFramework(),
                        TeacherQualificationStatus.APPROVED)) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Năng lực giảng dạy này đã được duyệt.",
                    HttpStatus.CONFLICT);
        }

        String normalizedCode = credentialRegistry.claim(teacher.getId(), profile.proficiencyFramework(),
                credentialType, issuer, credentialCode);

        byte[] content = readAndValidateEvidence(evidence);
        UUID qualificationId = UUID.randomUUID();
        Path storedPath = storeEvidence(teacher.getId(), qualificationId, evidence, content);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(storedPath);
            }
        });
        TeacherLanguageQualification qualification = new TeacherLanguageQualification();
        qualification.setId(qualificationId);
        qualification.setTeacher(teacher);
        qualification.setLearningLanguage(profile.learningLanguage());
        qualification.setProficiencyFramework(profile.proficiencyFramework());
        qualification.setFrameworkVersion(profile.frameworkVersion());
        qualification.setCredentialLevel(profile.proficiencyLevel());
        qualification.setCredentialType(credentialType);
        qualification.setCredentialCode(normalizedCode);
        qualification.setIssuer(issuer.trim());
        qualification.setEvidencePath(storedPath.toString());
        qualification.setEvidenceFileName(safeFileName(evidence.getOriginalFilename()));
        qualification.setEvidenceMimeType(evidence.getContentType());
        qualification.setEvidenceFileSize(content.length);
        qualification.setEvidenceSha256(sha256(content));
        qualification.setCopyrightAgreed(true);
        qualification.setStatus(TeacherQualificationStatus.PENDING);

        try {
            qualification = qualificationRepository.saveAndFlush(qualification);
        } catch (RuntimeException exception) {
            deleteQuietly(storedPath);
            throw exception;
        }

        if (teacher.getKycStatus() != TeacherKycStatus.APPROVED) {
            teacher.setKycStatus(TeacherKycStatus.PENDING);
        }
        grantTeacherRoleIfAbsent(userId);
        teacherProfileRepository.save(teacher);

        notificationService.createNotificationForAdminRole(
                "COURSE_MANAGER",
                "Hồ sơ năng lực giáo viên chờ duyệt",
                teacher.getDisplayName() + " đã nộp hồ sơ "
                        + profile.proficiencyFramework().name() + " " + profile.proficiencyLevel() + ".",
                NotificationTypes.KYC_CERTIFICATE_REVIEW,
                "/admin/kyc");
        auditLogService.logUserAction(
                userId,
                "TEACHER_CANDIDATE",
                "SUBMIT_LANGUAGE_QUALIFICATION",
                "TEACHER_LANGUAGE_QUALIFICATION",
                qualification.getId(),
                Map.of(),
                Map.of(
                        "status", qualification.getStatus().name(),
                        "learningLanguage", qualification.getLearningLanguage().name(),
                        "framework", qualification.getProficiencyFramework().name()),
                Map.of("evidenceSha256", qualification.getEvidenceSha256()));
        return toResponse(qualification, false);
    }

    @Transactional(readOnly = true)
    public List<TeacherQualificationResponse> listForReview(TeacherQualificationStatus status) {
        TeacherQualificationStatus resolvedStatus = status == null
                ? TeacherQualificationStatus.PENDING
                : status;
        return qualificationRepository.findByStatusOrderBySubmittedAtAsc(resolvedStatus)
                .stream()
                .map(qualification -> toResponse(qualification, true))
                .toList();
    }

    @Transactional
    public TeacherQualificationResponse decide(
            UUID qualificationId,
            TeacherQualificationDecisionRequest request,
            UUID adminId
    ) {
        TeacherLanguageQualification qualification = qualificationRepository.findForUpdate(qualificationId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Không tìm thấy hồ sơ năng lực giáo viên.",
                        HttpStatus.NOT_FOUND));
        if (qualification.getStatus() != TeacherQualificationStatus.PENDING) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Hồ sơ này đã được xử lý.",
                    HttpStatus.CONFLICT);
        }
        if (!request.approved() && !StringUtils.hasText(request.reviewNote())) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Cần nhập lý do khi từ chối hồ sơ.");
        }

        TeacherQualificationStatus nextStatus = request.approved()
                ? TeacherQualificationStatus.APPROVED
                : TeacherQualificationStatus.REJECTED;
        qualification.setStatus(nextStatus);
        qualification.setReviewNote(blankToNull(request.reviewNote()));
        qualification.setReviewedBy(adminId);
        qualification.setReviewedAt(Instant.now());

        TeacherProfile teacher = teacherProfileRepository.findForUpdateByUserId(qualification.getTeacher().getUser().getId())
                .orElseThrow(() -> new BusinessException(MessageCodes.MSG_KYC_010, "Không tìm thấy giáo viên."));
        entityManager.refresh(teacher);
        if (teacher.getKycStatus() == TeacherKycStatus.REVOKED) {
            throw new BusinessException(MessageCodes.MSG_KYC_010, "Cần giải quyết việc thu hồi quyền giảng dạy trước khi duyệt năng lực.", HttpStatus.CONFLICT);
        }
        if (nextStatus == TeacherQualificationStatus.APPROVED) {
            teacher.setKycStatus(TeacherKycStatus.APPROVED);
            teacher.setCanPublishCourse(true);
            grantTeacherRoleIfAbsent(teacher.getUser().getId());
        } else if (!qualificationRepository.existsByTeacher_IdAndStatus(
                teacher.getId(), TeacherQualificationStatus.APPROVED)
                && !kycRequestRepository.existsByTeacherProfileIdAndStatus(teacher.getId(), com.manabihub.kyc.domain.KycRequestStatus.APPROVED)) {
            teacher.setKycStatus(TeacherKycStatus.CORRECTION_REQUIRED);
            teacher.setCanPublishCourse(false);
        }
        teacherProfileRepository.save(teacher);
        qualificationRepository.saveAndFlush(qualification);

        if (nextStatus == TeacherQualificationStatus.REJECTED) {
            credentialRegistry.releaseRejected(teacher.getId(), qualification.getProficiencyFramework(),
                    qualification.getCredentialType(), qualification.getIssuer(), qualification.getCredentialCode());
        }

        notificationService.createNotification(
                teacher.getUser().getId(),
                teacher.getUser().getEmail(),
                nextStatus == TeacherQualificationStatus.APPROVED
                        ? "Năng lực giảng dạy đã được duyệt"
                        : "Hồ sơ năng lực cần bổ sung",
                nextStatus == TeacherQualificationStatus.APPROVED
                        ? "Bạn có thể xuất bản khóa học " + qualification.getLearningLanguage().name()
                                + " theo khung " + qualification.getProficiencyFramework().name() + "."
                        : "Hồ sơ chưa được duyệt. Lý do: " + qualification.getReviewNote(),
                NotificationTypes.KYC_RESULT,
                "/teacher/kyc");
        auditLogService.logAdminAction(
                adminId,
                "COURSE_MANAGER",
                "REVIEW_LANGUAGE_QUALIFICATION",
                "TEACHER_LANGUAGE_QUALIFICATION",
                qualification.getId(),
                Map.of("status", TeacherQualificationStatus.PENDING.name()),
                Map.of("status", nextStatus.name()),
                Map.of(
                        "teacherId", teacher.getId().toString(),
                        "learningLanguage", qualification.getLearningLanguage().name(),
                        "framework", qualification.getProficiencyFramework().name()));
        return toResponse(qualification, true);
    }

    @Transactional(readOnly = true)
    public TeacherQualificationEvidenceDownload getTeacherEvidence(UUID qualificationId, UUID userId) {
        TeacherLanguageQualification qualification = qualificationRepository
                .findByIdAndTeacher_User_Id(qualificationId, userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Không tìm thấy tài liệu.",
                        HttpStatus.NOT_FOUND));
        return loadEvidence(qualification);
    }

    @Transactional(readOnly = true)
    public TeacherQualificationEvidenceDownload getAdminEvidence(UUID qualificationId) {
        TeacherLanguageQualification qualification = qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Không tìm thấy tài liệu.",
                        HttpStatus.NOT_FOUND));
        return loadEvidence(qualification);
    }

    @Transactional(readOnly = true)
    public void requireApprovedForCourse(TeacherProfile teacher, Course course) {
        if (teacher.getKycStatus() != TeacherKycStatus.APPROVED || !teacher.isCanPublishCourse()) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_010,
                    "Hồ sơ danh tính và năng lực giảng dạy phải được duyệt trước khi xuất bản.",
                    HttpStatus.FORBIDDEN);
        }
        LearningLanguage language = course.getLearningLanguage() == null
                ? LearningLanguage.JAPANESE
                : course.getLearningLanguage();
        ProficiencyFramework framework = course.getProficiencyFramework() == null
                ? ProficiencyFramework.JLPT
                : course.getProficiencyFramework();
        boolean approved = qualificationRepository
                .existsByTeacher_IdAndLearningLanguageAndProficiencyFrameworkAndStatus(
                        teacher.getId(), language, framework, TeacherQualificationStatus.APPROVED);
        boolean legacyJapaneseApproval = language == LearningLanguage.JAPANESE
                && framework == ProficiencyFramework.JLPT
                && kycRequestRepository.existsByTeacherProfileIdAndStatus(
                        teacher.getId(), com.manabihub.kyc.domain.KycRequestStatus.APPROVED);
        if (!approved && !legacyJapaneseApproval) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_010,
                    "Giáo viên chưa được duyệt năng lực cho " + language.name()
                            + " theo khung " + framework.name() + ".",
                    HttpStatus.FORBIDDEN);
        }
    }

    private void validateSubmission(
            CourseLanguageProfile profile,
            TeacherCredentialType credentialType,
            String credentialCode,
            String issuer,
            boolean copyrightAgreementAccepted,
            MultipartFile evidence
    ) {
        if (credentialType == null || !StringUtils.hasText(issuer)) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Loại hồ sơ và đơn vị cấp/xác nhận là bắt buộc.");
        }
        if (issuer.length() > 255 || (credentialCode != null && credentialCode.length() > 120)) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Đơn vị cấp tối đa 255 ký tự; mã chứng chỉ tối đa 120 ký tự.");
        }
        if (!copyrightAgreementAccepted) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Bạn cần chấp nhận cam kết bản quyền và tính trung thực của hồ sơ.");
        }
        if (credentialType == TeacherCredentialType.OFFICIAL_CERTIFICATE
                && !StringUtils.hasText(credentialCode)) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Mã chứng chỉ là bắt buộc với chứng chỉ chính thức.");
        }
        if (profile.proficiencyFramework() != ProficiencyFramework.SOFL_INTERNAL
                && credentialType == TeacherCredentialType.SOFL_INTERNAL_APPROVAL) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Hồ sơ nội bộ SOFL chỉ dùng với khung trình độ nội bộ SOFL.");
        }
        if (evidence == null || evidence.isEmpty()) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Cần tải lên tài liệu chứng minh năng lực.");
        }
    }

    private byte[] readAndValidateEvidence(MultipartFile evidence) {
        if (evidence.getSize() > MAX_EVIDENCE_SIZE) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Tài liệu không được vượt quá 8MB.");
        }
        if (!SUPPORTED_MIME_TYPES.contains(evidence.getContentType())) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Chỉ chấp nhận PDF, JPG hoặc PNG.");
        }
        try {
            byte[] content = evidence.getBytes();
            if (!matchesDeclaredType(content, evidence.getContentType())) {
                throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                        "Nội dung tệp không khớp với định dạng đã khai báo.");
            }
            return content;
        } catch (IOException exception) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Không thể đọc tài liệu tải lên.", exception);
        }
    }

    private boolean matchesDeclaredType(byte[] content, String mimeType) {
        if (content.length < 4) {
            return false;
        }
        return switch (mimeType) {
            case "application/pdf" -> content[0] == '%' && content[1] == 'P'
                    && content[2] == 'D' && content[3] == 'F';
            case "image/png" -> content.length >= 8
                    && (content[0] & 0xff) == 0x89 && content[1] == 'P'
                    && content[2] == 'N' && content[3] == 'G';
            case "image/jpeg" -> (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8;
            default -> false;
        };
    }

    private Path storeEvidence(
            UUID teacherId,
            UUID qualificationId,
            MultipartFile evidence,
            byte[] content
    ) {
        String extension = switch (evidence.getContentType()) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
        Path root = Path.of(storageRoot).toAbsolutePath().normalize();
        Path directory = root.resolve("qualifications").resolve(teacherId.toString()).normalize();
        Path target = directory.resolve(qualificationId + extension).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST, "Đường dẫn lưu tài liệu không hợp lệ.");
        }
        try {
            Files.createDirectories(directory);
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return target;
        } catch (IOException exception) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Không thể lưu tài liệu tải lên.", exception);
        }
    }

    private TeacherQualificationEvidenceDownload loadEvidence(TeacherLanguageQualification qualification) {
        if (qualification.getEvidencePath().startsWith("legacy://")) {
            throw new BusinessException(MessageCodes.COMMON_NOT_FOUND,
                    "Hồ sơ cũ không có tệp điện tử để tải xuống.", HttpStatus.NOT_FOUND);
        }
        Path root = Path.of(storageRoot).toAbsolutePath().normalize();
        Path path = Path.of(qualification.getEvidencePath()).toAbsolutePath().normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new BusinessException(MessageCodes.COMMON_NOT_FOUND,
                    "Không tìm thấy tài liệu.", HttpStatus.NOT_FOUND);
        }
        try {
            return new TeacherQualificationEvidenceDownload(
                    Files.readAllBytes(path),
                    qualification.getEvidenceFileName(),
                    qualification.getEvidenceMimeType());
        } catch (IOException exception) {
            throw new BusinessException(MessageCodes.COMMON_NOT_FOUND,
                    "Không thể đọc tài liệu.", HttpStatus.NOT_FOUND, exception);
        }
    }

    private TeacherQualificationResponse toResponse(
            TeacherLanguageQualification qualification,
            boolean admin
    ) {
        boolean legacy = qualification.getEvidencePath().startsWith("legacy://");
        String evidenceUrl = legacy ? null : admin
                ? "/v1/admin/teacher-qualifications/" + qualification.getId() + "/evidence"
                : "/v1/teacher/kyc/qualifications/" + qualification.getId() + "/evidence";
        return new TeacherQualificationResponse(
                qualification.getId(),
                qualification.getTeacher().getId(),
                qualification.getTeacher().getDisplayName(),
                qualification.getTeacher().getUser() == null
                        ? null
                        : qualification.getTeacher().getUser().getEmail(),
                qualification.getLearningLanguage(),
                qualification.getProficiencyFramework(),
                qualification.getFrameworkVersion(),
                qualification.getCredentialLevel(),
                qualification.getCredentialType(),
                maskCode(qualification.getCredentialCode()),
                qualification.getIssuer(),
                qualification.getEvidenceFileName(),
                evidenceUrl,
                qualification.getStatus(),
                qualification.getReviewNote(),
                qualification.getSubmittedAt(),
                qualification.getReviewedAt());
    }

    private void grantTeacherRoleIfAbsent(UUID userId) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM user_roles WHERE user_id = :userId AND role_id = :roleId")
                .setParameter("userId", userId)
                .setParameter("roleId", TEACHER_ROLE_ID)
                .getSingleResult();
        if (count.longValue() == 0) {
            entityManager.createNativeQuery(
                            "INSERT INTO user_roles (user_id, role_id) VALUES (:userId, :roleId)")
                    .setParameter("userId", userId)
                    .setParameter("roleId", TEACHER_ROLE_ID)
                    .executeUpdate();
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "qualification-evidence";
        }
        String normalized = Path.of(fileName).getFileName().toString()
                .replaceAll("[\\r\\n\\t]", "_");
        return normalized.length() <= 255 ? normalized : normalized.substring(normalized.length() - 255);
    }

    private String maskCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() <= 4) {
            return "****";
        }
        return normalized.substring(0, 2) + "****" + normalized.substring(normalized.length() - 2);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The database operation remains the primary error. Storage cleanup can be retried operationally.
        }
    }
}
