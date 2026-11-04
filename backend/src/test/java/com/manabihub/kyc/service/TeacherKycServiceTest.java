package com.manabihub.kyc.service;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.service.AccountIdentityVerificationService;
import com.manabihub.identity.entity.AccountIdentityVerification;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.CertificateVerificationStatus;
import com.manabihub.kyc.domain.IdentityVerificationStatus;
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.KycRequestStatus;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.kyc.dto.KycCertificateSubmissionResponse;
import com.manabihub.kyc.dto.KycIdentityVerificationRequest;
import com.manabihub.kyc.dto.KycIdentityVerificationResponse;
import com.manabihub.kyc.dto.KycStatusResponse;
import com.manabihub.kyc.port.VnptServerVerificationResult;
import com.manabihub.kyc.port.VnptVerificationPort;
import com.manabihub.kyc.repository.KycDocumentRepository;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.kyc.repository.VnptIdentityTransactionClaimRepository;
import com.manabihub.mock.domain.MockNationalIdRegistryRecord;
import com.manabihub.mock.repository.MockNationalIdRegistryRepository;
import com.manabihub.notification.repository.NotificationRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.security.service.PublicJwtTokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class TeacherKycServiceTest {
    @Mock
    private TeacherProfileRepository teacherProfileRepository;
    @Mock
    private KycRequestRepository kycRequestRepository;
    @Mock
    private KycDocumentRepository kycDocumentRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TeacherIdentityClaimService teacherIdentityClaimService;
    @Mock
    private TeacherCertificateClaimService teacherCertificateClaimService;
    @Mock
    private PublicJwtTokenService publicJwtTokenService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query countQuery;
    @Mock
    private Query insertQuery;
    @Mock private VnptVerificationPort vnptVerificationPort;
    @Mock private com.manabihub.audit.service.SecurityAuditService securityAuditService;
    @Mock private MockNationalIdRegistryRepository mockNationalIdRegistryRepository;
    @Mock private VnptVerificationCoordinator verificationCoordinator;
    @Mock private VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository;
    @Mock private AccountIdentityVerificationService accountIdentityVerificationService;
    @TempDir
    private Path storageRoot;
    private TeacherKycService teacherKycService;
    private AppUser user;
    private TeacherProfile teacher;
    private KycRequest identityVerifiedRequest;
    @BeforeEach
    void setUp() {
        teacherKycService = newTeacherKycService("server");
        user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("teacher@example.com");
        user.setFullName("Nguyen Van A");
        user.setUserStatus(UserStatus.ACTIVE);
        teacher = new TeacherProfile();
        teacher.setId(UUID.randomUUID());
        teacher.setUser(user);
        teacher.setKycStatus(TeacherKycStatus.NOT_SUBMITTED);
        teacher.setCanPublishCourse(false);
        identityVerifiedRequest = new KycRequest();
        identityVerifiedRequest.setId(UUID.randomUUID());
        identityVerifiedRequest.setTeacherProfile(teacher);
        identityVerifiedRequest.setStatus(KycRequestStatus.DRAFT);
        identityVerifiedRequest.setEkycProvider("VNPT_EKYC_WEB_SDK");
        identityVerifiedRequest.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        identityVerifiedRequest.setCertificateStatus(CertificateVerificationStatus.NOT_SUBMITTED);
        identityVerifiedRequest.setIdentityVerifiedAt(Instant.now());
        identityVerifiedRequest.setServerFullName("Nguyen Van A");
        identityVerifiedRequest.setServerDateOfBirth("1990-01-02");
        identityVerifiedRequest.setVerificationPayload(Map.of(
                "providerStatus", "SDK_VERIFIED",
                "identityOcr", Map.of(
                        "idNumber", "012345678901",
                        "fullName", "Nguyen Van A",
                        "dateOfBirth", "1990-01-02"
                )
        ));
        lenient().when(teacherCertificateClaimService.normalizeJlptCertificateCode(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class)
                        .replaceAll("[^A-Za-z0-9]", "")
                        .toUpperCase());
    }

    private TeacherKycService newTeacherKycService(String verificationMode) {
        return new TeacherKycService(
                teacherProfileRepository,
                kycRequestRepository,
                kycDocumentRepository,
                auditLogRepository,
                notificationRepository,
                notificationService,
                teacherIdentityClaimService,
                teacherCertificateClaimService,
                publicJwtTokenService,
                vnptVerificationPort,
                securityAuditService,
                mockNationalIdRegistryRepository,
                entityManager,
                verificationCoordinator,
                vnptIdentityTransactionClaimRepository,
                accountIdentityVerificationService,
                storageRoot.toString(),
                verificationMode
        );
    }

    @Test
    void getStatus_materializesIdentityVerifiedThroughStudentEntryPoint() {
        AccountIdentityVerification shared = new AccountIdentityVerification();
        shared.setUserId(user.getId());
        shared.setIdentityFingerprint("fingerprint");
        shared.setProvider("VNPT_EKYC_WEB_SDK_UAT");
        shared.setFullName("Nguyen Van A");
        shared.setDateOfBirth(java.time.LocalDate.of(1990, 1, 2));
        shared.setVerifiedAt(Instant.parse("2026-08-14T00:00:00Z"));
        shared.setSourceSubject("STUDENT");
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.empty());
        when(accountIdentityVerificationService.findVerified(user.getId()))
                .thenReturn(Optional.of(shared));
        when(kycRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            KycRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        KycStatusResponse response = teacherKycService.getStatus(user.getId());

        assertEquals("VERIFIED", response.identityVerification().status());
        assertEquals("NOT_SUBMITTED", response.certificateVerification().status());
        assertEquals("ACCOUNT_VERIFIED", response.latestRequest().verificationPayload().get("providerStatus"));
    }
    @Test
    void restartVerification_failsIfUserInactive() {
        user.setUserStatus(UserStatus.LOCKED);
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        BusinessException ex = assertThrows(BusinessException.class, () ->
            teacherKycService.restartVerification(user.getId(), "127.0.0.1", "Test"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    void restartVerification_failsIfKycApproved() {
        teacher.setKycStatus(TeacherKycStatus.APPROVED);
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        BusinessException ex = assertThrows(BusinessException.class, () ->
            teacherKycService.restartVerification(user.getId(), "127.0.0.1", "Test"));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals(MessageCodes.KYC_ALREADY_APPROVED, ex.getMessageCode());
    }

    @Test
    void restartVerification_createsCandidateIfNotFound() {
        when(kycRequestRepository.save(any())).thenAnswer(inv -> {
            KycRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(teacherProfileRepository.findByUserId(user.getId()))
                .thenReturn(Optional.empty()) // First call returns empty
                .thenReturn(Optional.of(teacher)); // Second call returns the created profile

        teacherKycService.restartVerification(user.getId(), "127.0.0.1", "Test");
        verify(teacherProfileRepository).createCandidateIfAbsent(any(), eq(user.getId()));
    }

    @Test
    void getStatus_returnsApprovedTeacherState() {
        teacher.setKycStatus(TeacherKycStatus.APPROVED);
        teacher.setCanPublishCourse(true);
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.empty());
        KycStatusResponse response = teacherKycService.getStatus(user.getId());
        assertEquals("APPROVED", response.teacherKycStatus());
        assertTrue(response.canPublishCourse());
    }
    @Test
    void getStatus_createsCandidateProfileWhenMissing() {
        when(teacherProfileRepository.findByUserId(user.getId()))
                .thenReturn(Optional.empty(), Optional.of(teacher));
        when(teacherProfileRepository.createCandidateIfAbsent(any(UUID.class), eq(user.getId())))
                .thenReturn(1);
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.empty());
        KycStatusResponse response = teacherKycService.getStatus(user.getId());
        assertEquals("NOT_SUBMITTED", response.teacherKycStatus());
        assertFalse(response.canPublishCourse());
    }
    @Test
    void verifyIdentity_delegatesToCoordinatorAndReturnsResponse() {
        // when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(teacherProfileRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        KycRequest mockRequest = new KycRequest();
        mockRequest.setId(UUID.randomUUID());
        mockRequest.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        mockRequest.setEkycProvider("VNPT_EKYC_WEB_SDK");
        mockRequest.setIdentityVerifiedAt(Instant.parse("2026-08-14T00:00:00Z"));
        when(kycRequestRepository.findById(mockRequest.getId())).thenReturn(Optional.of(mockRequest));
        when(verificationCoordinator.orchestrate(any(), any(), any(), anyString(), anyString()))
                .thenReturn(new VnptVerificationCoordinator.VerificationOutcome(
                        mockRequest.getId(), teacher.getId(), user.getId(), IdentityVerificationStatus.VERIFIED, true
                ));
        when(teacherIdentityClaimService.normalizeCccd("012345678901"))
                .thenReturn("012345678901");
        when(teacherIdentityClaimService.generateFingerprint("012345678901"))
                .thenReturn("fingerprint");
        Map<String, Object> sdkResult = Map.of("object", Map.of(
                "idNumber", "012345678901",
                "fullName", "Nguyen Van A",
                "dateOfBirth", "1990-01-02"));
        var response = teacherKycService.verifyIdentity(
                user.getId(),
                new KycIdentityVerificationRequest("session", "transaction", sdkResult),
                "127.0.0.1",
                "JUnit"
        );
        assertEquals("VERIFIED", response.request().identityStatus());
        verify(verificationCoordinator).orchestrate(eq(user.getId()), any(), any(), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    void verifyIdentity_acceptsRealVnptNestedCallbackPayload() {
        when(teacherProfileRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        KycRequest mockRequest = new KycRequest();
        mockRequest.setId(UUID.randomUUID());
        mockRequest.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        mockRequest.setEkycProvider("VNPT_EKYC_WEB_SDK");
        when(kycRequestRepository.findById(mockRequest.getId())).thenReturn(Optional.of(mockRequest));
        when(verificationCoordinator.orchestrate(any(), any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    VnptSdkDecision decision = invocation.getArgument(2);
                    assertTrue(decision.verified());
                    assertEquals("0272400402711", decision.identityOcr().get("idNumber"));
                    assertEquals("NGUYEN XUAN DAT", decision.identityOcr().get("fullName"));
                    assertEquals("31/08/2004", decision.identityOcr().get("dateOfBirth"));
                    return new VnptVerificationCoordinator.VerificationOutcome(
                            mockRequest.getId(), teacher.getId(), user.getId(), IdentityVerificationStatus.VERIFIED, true
                    );
                });

        Map<String, Object> sdkResult = Map.of(
                "type_document", 9,
                "ocr", Map.of("object", Map.of(
                        "back_type_id", 9,
                        "id", "0272400402711",
                        "name", "NGUYEN XUAN DAT",
                        "birth_day", "31/08/2004",
                        "quality_front", Map.of("blur_score", 0.2D, "bright_spot_score", 0.1D),
                        "general_warning", List.of()
                )),
                "liveness_card_front", Map.of("object", Map.of(
                        "liveness", "success",
                        "fake_liveness", false,
                        "fake_liveness_prob", 0.05D
                )),
                "liveness_card_back", Map.of("object", Map.of(
                        "liveness", "success",
                        "fake_print_photo", false,
                        "fake_print_photo_prob", 0.04D
                )),
                "liveness_face", Map.of("object", Map.of(
                        "liveness", "success",
                        "blur_face", "no",
                        "blur_face_score", 0.12D,
                        "liveness_prob", 0.97D
                )),
                "compare", Map.of("object", Map.of("result", "", "prob", 0.9778D)),
                "masked", Map.of("object", Map.of("masked", "false"))
        );

        var response = teacherKycService.verifyIdentity(
                user.getId(),
                new KycIdentityVerificationRequest("session", "transaction", sdkResult),
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("VERIFIED", response.request().identityStatus());
    }

    @Test
    void verifyIdentity_directSdkMockMode_crossChecksSyntheticNationalIdRegistry() {
        teacherKycService = newTeacherKycService("direct-sdk-mock");
        user.setFullName("Display Alias");
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.empty());

        MockNationalIdRegistryRecord registryRecord = org.mockito.Mockito.mock(MockNationalIdRegistryRecord.class);
        when(registryRecord.getFullName()).thenReturn("Nguyen Van A");
        when(registryRecord.getDateOfBirth()).thenReturn(java.time.LocalDate.of(1990, 1, 2));
        when(mockNationalIdRegistryRepository.findByIdNumberAndActiveTrue("012345678901"))
                .thenReturn(Optional.of(registryRecord));
        when(teacherIdentityClaimService.normalizeCccd("012345678901"))
                .thenReturn("012345678901");
        when(kycRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            KycRequest request = invocation.getArgument(0);
            assertFalse(request.getVerificationPayload().containsKey("providerResult"));
            Map<?, ?> storedIdentity = (Map<?, ?>) request.getVerificationPayload().get("identityOcr");
            assertEquals("********8901", storedIdentity.get("idNumber"));
            request.setId(UUID.randomUUID());
            return request;
        });

        Map<String, Object> sdkResult = Map.of(
                "ocr", Map.of("object", Map.of(
                        "id", "012345678901",
                        "name", "Nguyen Van A",
                        "birth_day", "02/01/1990"
                )),
                "liveness_face", Map.of("object", Map.of("liveness", "success")),
                "compare", Map.of("object", Map.of("result", "match", "prob", 0.98D)),
                "masked", Map.of("object", Map.of("masked", "false"))
        );

        KycIdentityVerificationResponse response = teacherKycService.verifyIdentity(
                user.getId(),
                new KycIdentityVerificationRequest("session", "transaction", sdkResult),
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("VERIFIED", response.request().identityStatus());
        verify(mockNationalIdRegistryRepository).findByIdNumberAndActiveTrue("012345678901");
        verify(teacherIdentityClaimService).processIdentityClaim(
                eq(teacher.getId()),
                eq("012345678901"),
                eq(user),
                eq("127.0.0.1"),
                eq("JUnit")
        );
        verifyNoInteractions(verificationCoordinator);
    }

    @Test
    void verifyIdentity_directSdkModeUsesSessionReplayBindingWithoutMockRegistry() {
        teacherKycService = newTeacherKycService("direct-sdk");
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.empty());
        when(teacherIdentityClaimService.normalizeCccd("012345678901"))
                .thenReturn("012345678901");
        when(kycRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            KycRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        Map<String, Object> sdkResult = Map.of(
                "ocr", Map.of("object", Map.of(
                        "id", "012345678901",
                        "name", "Nguyen Van A",
                        "birth_day", "02/01/1990"
                )),
                "liveness_face", Map.of("object", Map.of("liveness", "success")),
                "compare", Map.of("object", Map.of("result", "match", "prob", 0.98D)),
                "masked", Map.of("object", Map.of("masked", "false"))
        );

        KycIdentityVerificationResponse response = teacherKycService.verifyIdentity(
                user.getId(),
                new KycIdentityVerificationRequest("session-uat", null, sdkResult),
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("VERIFIED", response.request().identityStatus());
        verify(vnptIdentityTransactionClaimRepository).saveAndFlush(argThat(claim ->
                "TEACHER".equals(claim.getSubjectType())
                        && "VNPT_EKYC_WEB_SDK".equals(claim.getProvider())
                        && "SESSION:session-uat".equals(claim.getProviderTransactionId())
                        && "session-uat".equals(claim.getProviderSessionId())));
        verifyNoInteractions(mockNationalIdRegistryRepository, verificationCoordinator);
    }

    @Test
    void verifyIdentity_directSdkMockMode_rejectsUnknownNationalId() {
        teacherKycService = newTeacherKycService("direct-sdk-mock");
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.empty());
        when(mockNationalIdRegistryRepository.findByIdNumberAndActiveTrue("012345678901"))
                .thenReturn(Optional.empty());
        when(teacherIdentityClaimService.normalizeCccd("012345678901"))
                .thenReturn("012345678901");
        when(kycRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            KycRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        Map<String, Object> sdkResult = Map.of(
                "ocr", Map.of("object", Map.of(
                        "id", "012345678901",
                        "name", "Nguyen Van A",
                        "birth_day", "02/01/1990"
                )),
                "liveness_face", Map.of("object", Map.of("liveness", "success")),
                "compare", Map.of("object", Map.of("result", "match", "prob", 0.98D)),
                "masked", Map.of("object", Map.of("masked", "false"))
        );

        KycIdentityVerificationResponse response = teacherKycService.verifyIdentity(
                user.getId(),
                new KycIdentityVerificationRequest("session", "transaction", sdkResult),
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("FAILED", response.request().identityStatus());
        verify(teacherIdentityClaimService, never()).processIdentityClaim(
                any(), anyString(), any(), anyString(), anyString()
        );
    }

    @Test
    void verifyIdentity_directSdkMockMode_rejectsDocumentOnlyLivenessWithoutFaceCheck() {
        teacherKycService = newTeacherKycService("direct-sdk-mock");
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.empty());
        when(kycRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            KycRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        Map<String, Object> sdkResult = Map.of(
                "ocr", Map.of("object", Map.of(
                        "id", "012345678901",
                        "name", "Nguyen Van A",
                        "birth_day", "02/01/1990"
                )),
                "liveness_card_front", Map.of("object", Map.of("liveness", "success")),
                "liveness_card_back", Map.of("object", Map.of("liveness", "success"))
        );

        KycIdentityVerificationResponse response = teacherKycService.verifyIdentity(
                user.getId(),
                new KycIdentityVerificationRequest("session", "transaction", sdkResult),
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("FAILED", response.request().identityStatus());
        verify(teacherIdentityClaimService, never()).processIdentityClaim(
                any(), anyString(), any(), anyString(), anyString()
        );
    }

    @Test
    void verifyIdentity_directSdkMockMode_rejectsVnptNoMatchBeforeRegistryLookup() {
        teacherKycService = newTeacherKycService("direct-sdk-mock");
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.empty());
        when(kycRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            KycRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        Map<String, Object> sdkResult = Map.of(
                "ocr", Map.of("object", Map.of(
                        "id", "012345678901",
                        "name", "Nguyen Van A",
                        "birth_day", "02/01/1990"
                )),
                "liveness_face", Map.of("object", Map.of("liveness", "success")),
                "compare", Map.of("object", Map.of("msg", "NOMATCH", "prob", 0.99D))
        );

        KycIdentityVerificationResponse response = teacherKycService.verifyIdentity(
                user.getId(),
                new KycIdentityVerificationRequest("session", "transaction", sdkResult),
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("FAILED", response.request().identityStatus());
        verifyNoInteractions(mockNationalIdRegistryRepository);
        verify(teacherIdentityClaimService, never()).processIdentityClaim(
                any(), anyString(), any(), anyString(), anyString()
        );
    }

    @Test
    void verifyIdentity_directSdkMode_acceptsDocumentQualityWarningWithoutMockRegistry() {
        teacherKycService = newTeacherKycService("direct-sdk");
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.empty());
        when(teacherIdentityClaimService.normalizeCccd("012345678901"))
                .thenReturn("012345678901");
        when(kycRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            KycRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        Map<String, Object> sdkResult = Map.of(
                "ocr", Map.of("object", Map.of(
                        "id", "012345678901",
                        "name", "Nguyen Van A",
                        "birth_day", "02/01/1990",
                        "general_warning", List.of("blurred_document")
                )),
                "liveness_face", Map.of("object", Map.of("liveness", "success")),
                "compare", Map.of("object", Map.of("result", "match", "prob", 0.98D))
        );

        KycIdentityVerificationResponse response = teacherKycService.verifyIdentity(
                user.getId(),
                new KycIdentityVerificationRequest("session", "transaction", sdkResult),
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("VERIFIED", response.request().identityStatus());
        verifyNoInteractions(mockNationalIdRegistryRepository);
        verify(teacherIdentityClaimService).processIdentityClaim(
                eq(teacher.getId()), eq("012345678901"), eq(user), eq("127.0.0.1"), eq("JUnit")
        );
    }

    @Test
    void submitCertificate_entersManualReviewAndGrantsTeacherWorkspace() {
        prepareCertificateSubmission();
        when(teacherCertificateClaimService.processCertificateClaim(
                eq(teacher.getId()),
                eq(identityVerifiedRequest.getId()),
                eq("JLPT2026001"),
                eq(user),
                anyString(),
                anyString()
        )).thenReturn("JLPT2026001");
        when(publicJwtTokenService.issueCurrentRoleToken(user.getId()))
                .thenReturn("fresh-teacher-token");
        KycCertificateSubmissionResponse response = teacherKycService.submitCertificate(
                user.getId(),
                validPng(),
                "JLPT-2026-001",
                "Nguyen Van A",
                "1990-01-02",
                "N2",
                "Name Nguyen Van A Date of Birth 1990-01-02 Level N2 Certificate JLPT-2026-001",
                true,
                "127.0.0.1",
                "JUnit"
        );
        assertEquals("PENDING", response.teacherKycStatus());
        assertFalse(response.canPublishCourse());
        assertTrue(response.teacherWorkspaceAvailable());
        assertEquals("fresh-teacher-token", response.sessionToken());
        assertEquals(KycRequestStatus.PENDING, identityVerifiedRequest.getStatus());
        assertEquals(
                CertificateVerificationStatus.PENDING_REVIEW,
                identityVerifiedRequest.getCertificateStatus()
        );
        verify(entityManager).createNativeQuery(startsWith("INSERT INTO user_roles"));
        verify(teacherProfileRepository).save(teacher);
    }
    @Test
    void submitCertificate_rejectsNameMismatchBeforeClaimOrFileStorage() {
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.of(identityVerifiedRequest));
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teacherKycService.submitCertificate(
                        user.getId(),
                        validPng(),
                        "JLPT-2026-001",
                        "Tran Van B",
                        "1990-01-02",
                        "N2",
                        "Name Tran Van B Date of Birth 1990-01-02 Level N2 Certificate JLPT-2026-001",
                        true,
                        "127.0.0.1",
                        "JUnit"
                )
        );
        assertEquals(MessageCodes.KYC_CERTIFICATE_OCR_MISMATCH, exception.getMessageCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        verify(teacherCertificateClaimService, never()).processCertificateClaim(
                any(), any(), anyString(), any(), anyString(), anyString()
        );
        verify(kycDocumentRepository, never()).save(any());
    }

    @Test
    void submitCertificate_rejectsDobMismatchBeforeClaimOrFileStorage() {
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.of(identityVerifiedRequest));
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teacherKycService.submitCertificate(
                        user.getId(),
                        validPng(),
                        "JLPT-2026-001",
                        "Nguyen Van A",
                        "1995-05-05",
                        "N2",
                        "Name Nguyen Van A Date of Birth 1995-05-05 Level N2 Certificate JLPT-2026-001",
                        true,
                        "127.0.0.1",
                        "JUnit"
                )
        );
        assertEquals(MessageCodes.KYC_CERTIFICATE_OCR_MISMATCH, exception.getMessageCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        verify(teacherCertificateClaimService, never()).processCertificateClaim(
                any(), any(), anyString(), any(), anyString(), anyString()
        );
        verify(kycDocumentRepository, never()).save(any());
    }

    @Test
    void submitCertificate_rejectsCertificateCodeThatWasNotReadFromImage() {
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.of(identityVerifiedRequest));
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teacherKycService.submitCertificate(
                        user.getId(),
                        validPng(),
                        "JLPT-2026-001",
                        "Nguyen Van A",
                        "1990-01-02",
                        "N2",
                        "Name Nguyen Van A Date of Birth 1990-01-02 Level N2 Certificate OTHER-999",
                        true,
                        "127.0.0.1",
                        "JUnit"
                )
        );
        assertEquals(MessageCodes.KYC_CERTIFICATE_OCR_MISMATCH, exception.getMessageCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        verify(teacherCertificateClaimService, never()).processCertificateClaim(
                any(), any(), anyString(), any(), anyString(), anyString()
        );
        verify(kycDocumentRepository, never()).save(any());
    }
    @Test
    void submitCertificate_propagatesDuplicateJlptConflict() {
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.of(identityVerifiedRequest));
        when(teacherCertificateClaimService.processCertificateClaim(
                any(), any(), anyString(), any(), anyString(), anyString()
        )).thenThrow(new BusinessException(
                MessageCodes.KYC_CERTIFICATE_ALREADY_CLAIMED,
                "Duplicate JLPT",
                HttpStatus.CONFLICT
        ));
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teacherKycService.submitCertificate(
                        user.getId(),
                        validPng(),
                        "JLPT-2026-001",
                        "Nguyen Van A",
                        "1990-01-02",
                        "N2",
                        "Name Nguyen Van A Date of Birth 1990-01-02 Level N2 Certificate JLPT-2026-001",
                        true,
                        "127.0.0.1",
                        "JUnit"
                )
        );
        assertEquals(MessageCodes.KYC_CERTIFICATE_ALREADY_CLAIMED, exception.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        verify(kycDocumentRepository, never()).save(any());
    }

    @Test
    void submitCertificate_acceptsMatchingNameAndDobWithDifferentFormattingAndDiacritics() {
        prepareCertificateSubmission();
        identityVerifiedRequest.setServerFullName("PHẠM ĐỨC LONG");
        identityVerifiedRequest.setServerDateOfBirth("20/05/2000");

        KycCertificateSubmissionResponse response = teacherKycService.submitCertificate(
                user.getId(),
                validPng(),
                "25B2080102-33745",
                "PHAM DUC LONG",
                "2000-05-20",
                "N3",
                "Name PHAM DUC LONG Date of Birth 2000-05-20 Level N3 Certificate 25B2080102-33745",
                true,
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("PENDING", response.teacherKycStatus());
        assertFalse(response.canPublishCourse());
        assertTrue(response.teacherWorkspaceAvailable());
    }

    @Test
    void submitCertificate_rejectsBothNameAndDobMismatchAsBusinessErrorNot500() {
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.of(identityVerifiedRequest));
        identityVerifiedRequest.setServerFullName("PHẠM ĐỨC LONG");
        identityVerifiedRequest.setServerDateOfBirth("20/05/2000");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teacherKycService.submitCertificate(
                        user.getId(),
                        validPng(),
                        "25B2080102-33745",
                        "THAN VAN THANH",
                        "2004-08-12",
                        "N3",
                        "Name THAN VAN THANH Date of Birth 2004-08-12 Level N3 Certificate 25B2080102-33745",
                        true,
                        "127.0.0.1",
                        "JUnit"
                )
        );

        assertEquals(MessageCodes.KYC_CERTIFICATE_OCR_MISMATCH, exception.getMessageCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        verify(teacherCertificateClaimService, never()).processCertificateClaim(
                any(), any(), anyString(), any(), anyString(), anyString()
        );
        verify(kycDocumentRepository, never()).save(any());
    }
    private void prepareCertificateSubmission() {
        when(teacherProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacher.getId()))
                .thenReturn(Optional.of(identityVerifiedRequest));
        when(kycRequestRepository.saveAndFlush(identityVerifiedRequest))
                .thenReturn(identityVerifiedRequest);
        when(kycDocumentRepository.findByKycRequestIdOrderByCreatedAtAsc(identityVerifiedRequest.getId()))
                .thenAnswer(invocation -> List.of());
        when(notificationRepository.findActiveAdminIdsByRoleCode("COURSE_MANAGER"))
                .thenReturn(List.of(UUID.randomUUID()));
        when(entityManager.createNativeQuery(startsWith("SELECT COUNT(*) FROM user_roles")))
                .thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);
        when(entityManager.createNativeQuery(startsWith("INSERT INTO user_roles")))
                .thenReturn(insertQuery);
        when(insertQuery.setParameter(anyString(), any())).thenReturn(insertQuery);
        when(insertQuery.executeUpdate()).thenReturn(1);
    }

    private MockMultipartFile validPng() {
        byte[] bytes = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01
        };
        return new MockMultipartFile(
                "certificate",
                "jlpt.png",
                "image/png",
                bytes
        );
    }
}
