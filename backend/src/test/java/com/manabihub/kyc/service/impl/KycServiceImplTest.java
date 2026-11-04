package com.manabihub.kyc.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.KycRequestStatus;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.dto.request.KycReviewRequest;
import com.manabihub.kyc.dto.response.KycRequestResponse;
import com.manabihub.kyc.repository.InternalAdminAccountRepository;
import com.manabihub.kyc.repository.KycDocumentRepository;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.enums.WalletOwnerType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycServiceImplTest {

    @Mock
    private KycRequestRepository kycRequestRepository;
    @Mock
    private InternalAdminAccountRepository adminAccountRepository;
    @Mock
    private TeacherProfileRepository teacherProfileRepository;
    @Mock
    private KycDocumentRepository kycDocumentRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private WalletRepository walletRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query countQuery;
    @Mock
    private Query trustQuery;
    @Mock
    private Query mutationQuery;

    @InjectMocks
    private KycServiceImpl kycService;

    private InternalAdminAccount courseManager;
    private TeacherProfile teacherProfile;
    private KycRequest pendingRequest;
    private UUID kycId;

    @BeforeEach
    void setUp() {
        kycId = UUID.randomUUID();
        courseManager = new InternalAdminAccount();
        courseManager.setId(UUID.randomUUID());
        courseManager.setEmail("manager@manabihub.local");
        courseManager.setAccountStatus("ACTIVE");

        AppUser teacherUser = new AppUser();
        teacherUser.setId(UUID.randomUUID());
        teacherUser.setEmail("teacher@manabihub.local");
        teacherUser.setFullName("Nguyen Van A");

        teacherProfile = new TeacherProfile();
        teacherProfile.setId(UUID.randomUUID());
        teacherProfile.setUser(teacherUser);
        teacherProfile.setKycStatus(TeacherKycStatus.PENDING);

        pendingRequest = new KycRequest();
        pendingRequest.setId(kycId);
        pendingRequest.setTeacherProfile(teacherProfile);
        pendingRequest.setStatus(KycRequestStatus.PENDING);
        pendingRequest.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        pendingRequest.setCertificateStatus(CertificateVerificationStatus.PENDING_REVIEW);
        pendingRequest.setCertificateCode("JLPT20260001");
        pendingRequest.setVerificationPayload(validCertificatePayload());
        pendingRequest.setCreatedAt(Instant.now());
        pendingRequest.setUpdatedAt(Instant.now());
    }

    @Test
    void getPendingQueue_returnsOnlyActionablePendingRecords() {
        allowCourseManager();
        when(kycRequestRepository.findByStatusOrderByCreatedAtDesc(KycRequestStatus.PENDING))
                .thenReturn(List.of(pendingRequest));
        when(kycDocumentRepository.findByKycRequestIdOrderByCreatedAtAsc(kycId))
                .thenReturn(List.of());

        List<KycRequestResponse> response =
                kycService.getPendingKycQueue(courseManager.getId());

        assertEquals(1, response.size());
        assertEquals(KycRequestStatus.PENDING, response.getFirst().getStatus());
        assertEquals("JLPT_AUTHENTICITY_CHECK", response.getFirst().getExceptionType());
    }

    @Test
    void review_rejectsAdminWithoutLiveDatabaseRole() {
        UUID adminId = UUID.randomUUID();
        InternalAdminAccount admin = new InternalAdminAccount();
        admin.setId(adminId);
        when(adminAccountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminAccountRepository.findActiveRoleCodesByAdminId(eq(adminId), anyList()))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycService.reviewKyc(
                        kycId,
                        new KycReviewRequest(KycRequestStatus.APPROVED, "OK"),
                        adminId
                )
        );

        assertEquals(MessageCodes.ADMIN_PERMISSION_DENIED, exception.getMessageCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        verify(kycRequestRepository, never()).save(any());
    }

    @Test
    void review_rejectRequiresDecisionReason() {
        allowCourseManager();
        when(kycRequestRepository.findByIdForReview(kycId))
                .thenReturn(Optional.of(pendingRequest));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycService.reviewKyc(
                        kycId,
                        new KycReviewRequest(KycRequestStatus.REJECTED, " "),
                        courseManager.getId()
                )
        );

        assertEquals(MessageCodes.VALIDATION_FAILED, exception.getMessageCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void review_approveRequiresVerifiedIdentityAndMatchedUniqueJlpt() {
        allowCourseManager();
        pendingRequest.setVerificationPayload(Map.of(
                "exceptionStage", "CERTIFICATE",
                "exceptionType", "JLPT_AUTHENTICITY_CHECK",
                "certificateType", "JLPT",
                "identityCrossMatch", "MISMATCHED",
                "duplicateCertificateCheck", "PASSED"
        ));
        when(kycRequestRepository.findByIdForReview(kycId))
                .thenReturn(Optional.of(pendingRequest));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycService.reviewKyc(
                        kycId,
                        new KycReviewRequest(KycRequestStatus.APPROVED, "Checked"),
                        courseManager.getId()
                )
        );

        assertEquals(MessageCodes.COMMON_CONFLICT, exception.getMessageCode());
        verify(teacherProfileRepository, never()).save(any());
    }

    @Test
    void review_approveUnlocksPublishingAndKeepsTeacherRole() {
        allowCourseManager();
        when(kycRequestRepository.findByIdForReview(kycId))
                .thenReturn(Optional.of(pendingRequest));
        prepareSuccessfulSave();
        prepareRoleCount(1);

        KycRequestResponse response = kycService.reviewKyc(
                kycId,
                new KycReviewRequest(KycRequestStatus.APPROVED, "Verified via Japan Foundation"),
                courseManager.getId()
        );

        assertEquals(KycRequestStatus.APPROVED, response.getStatus());
        assertEquals(CertificateVerificationStatus.APPROVED, pendingRequest.getCertificateStatus());
        assertEquals(TeacherKycStatus.APPROVED, teacherProfile.getKycStatus());
        assertTrue(teacherProfile.isCanPublishCourse());
        verify(teacherProfileRepository).save(teacherProfile);
    }

    @Test
    void review_correctionRevokesTemporaryTeacherWorkspaceRole() {
        allowCourseManager();
        when(kycRequestRepository.findByIdForReview(kycId))
                .thenReturn(Optional.of(pendingRequest));
        prepareSuccessfulSave();
        prepareMutationQuery();

        KycRequestResponse response = kycService.reviewKyc(
                kycId,
                new KycReviewRequest(
                        KycRequestStatus.CORRECTION_REQUIRED,
                        "Please upload a clearer original certificate"
                ),
                courseManager.getId()
        );

        assertEquals(KycRequestStatus.CORRECTION_REQUIRED, response.getStatus());
        assertEquals(TeacherKycStatus.CORRECTION_REQUIRED, teacherProfile.getKycStatus());
        assertFalse(teacherProfile.isCanPublishCourse());
        verify(entityManager).createNativeQuery(contains("DELETE FROM user_roles"));
    }

    @Test
    void review_revokeRequiresTrustCaseId() {
        allowCourseManager();
        KycRequest approved = approvedRequest();
        when(kycRequestRepository.findByIdForReview(kycId)).thenReturn(Optional.of(approved));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycService.reviewKyc(
                        kycId,
                        new KycReviewRequest(KycRequestStatus.REVOKED, "Confirmed fraud"),
                        courseManager.getId()
                )
        );

        assertEquals(MessageCodes.KYC_TRUST_CASE_REQUIRED, exception.getMessageCode());
    }

    @Test
    void review_revokeRejectsUnconfirmedTrustCase() {
        allowCourseManager();
        KycRequest approved = approvedRequest();
        when(kycRequestRepository.findByIdForReview(kycId)).thenReturn(Optional.of(approved));
        prepareTrustQuery(false);
        KycReviewRequest request = new KycReviewRequest(
                KycRequestStatus.REVOKED,
                "Confirmed fraud",
                UUID.randomUUID()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycService.reviewKyc(kycId, request, courseManager.getId())
        );

        assertEquals(MessageCodes.KYC_TRUST_CASE_REQUIRED, exception.getMessageCode());
        verify(teacherProfileRepository, never()).save(any());
    }

    @Test
    void review_revokeWithConfirmedTrustCaseSuspendsMarketplaceAndWallet() {
        allowCourseManager();
        KycRequest approved = approvedRequest();
        when(kycRequestRepository.findByIdForReview(kycId)).thenReturn(Optional.of(approved));
        prepareSuccessfulSave();
        prepareTrustQuery(true);
        prepareMutationQuery();

        Course publishedCourse = Course.builder()
                .id(UUID.randomUUID())
                .teacher(teacherProfile)
                .status(CourseStatus.PUBLISHED)
                .build();
        Course draftCourse = Course.builder()
                .id(UUID.randomUUID())
                .teacher(teacherProfile)
                .status(CourseStatus.DRAFT)
                .build();
        when(courseRepository.findByTeacher_IdAndStatusNotOrderByCreatedAtDesc(
                teacherProfile.getId(),
                CourseStatus.ARCHIVED
        )).thenReturn(List.of(publishedCourse, draftCourse));
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .teacher(teacherProfile)
                .frozen(false)
                .build();
        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(com.manabihub.wallet.enums.WalletOwnerType.TEACHER, teacherProfile.getId()))
                .thenReturn(Optional.of(wallet));
        KycReviewRequest request = new KycReviewRequest(
                KycRequestStatus.REVOKED,
                "Confirmed trust and safety violation",
                UUID.randomUUID()
        );

        KycRequestResponse response =
                kycService.reviewKyc(kycId, request, courseManager.getId());

        assertEquals(KycRequestStatus.REVOKED, response.getStatus());
        assertEquals(TeacherKycStatus.REVOKED, teacherProfile.getKycStatus());
        assertFalse(teacherProfile.isCanPublishCourse());
        assertEquals(CourseStatus.FORCED_DRAFT, publishedCourse.getStatus());
        assertEquals(CourseStatus.DRAFT, draftCourse.getStatus());
        assertTrue(wallet.isFrozen());
        verify(courseRepository).saveAll(List.of(publishedCourse));
        verify(walletRepository).save(wallet);
    }

    @Test
    void review_pendingRequestCannotBeRevokedDirectly() {
        allowCourseManager();
        when(kycRequestRepository.findByIdForReview(kycId))
                .thenReturn(Optional.of(pendingRequest));
        KycReviewRequest request = new KycReviewRequest(
                KycRequestStatus.REVOKED,
                "Not allowed",
                UUID.randomUUID()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycService.reviewKyc(kycId, request, courseManager.getId())
        );

        assertEquals(MessageCodes.COMMON_CONFLICT, exception.getMessageCode());
    }

    private void allowCourseManager() {
        when(adminAccountRepository.findById(courseManager.getId()))
                .thenReturn(Optional.of(courseManager));
        when(adminAccountRepository.findActiveRoleCodesByAdminId(
                eq(courseManager.getId()),
                anyList()
        )).thenReturn(List.of("COURSE_MANAGER"));
    }

    private void prepareSuccessfulSave() {
        when(kycRequestRepository.save(any(KycRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(kycDocumentRepository.findByKycRequestIdOrderByCreatedAtAsc(kycId))
                .thenReturn(List.of());
    }

    private void prepareRoleCount(long count) {
        when(entityManager.createNativeQuery(contains("SELECT COUNT(*) FROM user_roles")))
                .thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(count);
    }

    private void prepareMutationQuery() {
        when(entityManager.createNativeQuery(contains("DELETE FROM user_roles")))
                .thenReturn(mutationQuery);
        when(mutationQuery.setParameter(anyString(), any())).thenReturn(mutationQuery);
        when(mutationQuery.executeUpdate()).thenReturn(1);
    }

    private void prepareTrustQuery(boolean confirmed) {
        when(entityManager.createNativeQuery(contains("SELECT EXISTS")))
                .thenReturn(trustQuery);
        when(trustQuery.setParameter(anyString(), any())).thenReturn(trustQuery);
        when(trustQuery.getSingleResult()).thenReturn(confirmed);
    }

    private KycRequest approvedRequest() {
        teacherProfile.setKycStatus(TeacherKycStatus.APPROVED);
        teacherProfile.setCanPublishCourse(true);
        KycRequest approved = new KycRequest();
        approved.setId(kycId);
        approved.setTeacherProfile(teacherProfile);
        approved.setStatus(KycRequestStatus.APPROVED);
        approved.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        approved.setCertificateStatus(CertificateVerificationStatus.APPROVED);
        approved.setCreatedAt(Instant.now());
        approved.setUpdatedAt(Instant.now());
        return approved;
    }

    private Map<String, Object> validCertificatePayload() {
        return Map.of(
                "providerStatus", "SDK_VERIFIED",
                "exceptionStage", "CERTIFICATE",
                "exceptionType", "JLPT_AUTHENTICITY_CHECK",
                "certificateType", "JLPT",
                "identityCrossMatch", "MATCHED",
                "duplicateCertificateCheck", "PASSED",
                "certificateHolderName", "Nguyen Van A",
                "certificateDateOfBirth", "1990-01-02",
                "certificateLevel", "N2",
                "certificateOcrText", "Name Nguyen Van A DOB 1990-01-02 N2"
        );
    }
}
