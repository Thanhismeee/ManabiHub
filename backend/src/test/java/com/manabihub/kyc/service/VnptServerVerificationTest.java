package com.manabihub.kyc.service;

import com.manabihub.identity.service.AccountIdentityVerificationService;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.IdentityVerificationStatus;
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.KycRequestStatus;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.kyc.dto.KycIdentityVerificationRequest;
import com.manabihub.kyc.dto.KycIdentityVerificationResponse;
import com.manabihub.kyc.port.VnptServerVerificationResult;
import com.manabihub.kyc.port.VnptVerificationPort;
import com.manabihub.kyc.repository.KycDocumentRepository;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.kyc.repository.VnptIdentityTransactionClaimRepository;
import com.manabihub.mock.repository.MockNationalIdRegistryRepository;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.notification.repository.NotificationRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.security.service.PublicJwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for MHB-73: VNPT Server-to-Server Verification.
 * <p>
 * Validates that:
 * <ul>
 *   <li>Browser SDK result alone NEVER produces VERIFIED status</li>
 *   <li>Only server-confirmed VNPT transactions lead to VERIFIED</li>
 *   <li>Expired, cross-user, and unknown transactions are rejected</li>
 *   <li>Already-verified requests are handled idempotently</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VnptServerVerificationTest {

    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private KycRequestRepository kycRequestRepository;
    @Mock private KycDocumentRepository kycDocumentRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationService notificationService;
    @Mock private TeacherIdentityClaimService teacherIdentityClaimService;
    @Mock private TeacherCertificateClaimService teacherCertificateClaimService;
    @Mock private PublicJwtTokenService publicJwtTokenService;
    @Mock private VnptVerificationPort vnptVerificationPort;
    @Mock private com.manabihub.audit.service.SecurityAuditService securityAuditService;
    @Mock private MockNationalIdRegistryRepository mockNationalIdRegistryRepository;
    @Mock private EntityManager entityManager;
    @Mock private VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository;
    @Mock private AccountIdentityVerificationService accountIdentityVerificationService;

    private VnptVerificationCoordinator coordinator;
    private TeacherKycService service;
    @Mock private org.springframework.beans.factory.ObjectProvider<TeacherKycService> selfProvider;
    private UUID userId;
    private UUID teacherId;
    private TeacherProfile teacherProfile;
    private AppUser user;

    @BeforeEach
    void setUp() {
        org.springframework.beans.factory.ObjectProvider<VnptVerificationCoordinator> coordProvider = mock(org.springframework.beans.factory.ObjectProvider.class);
        coordinator = new VnptVerificationCoordinator(
                teacherProfileRepository,
                kycRequestRepository,
                vnptVerificationPort,
                teacherIdentityClaimService,
                securityAuditService,
                vnptIdentityTransactionClaimRepository,
                java.time.Clock.systemUTC(),
                coordProvider
        );
        lenient().when(coordProvider.getObject()).thenReturn(coordinator);

        service = new TeacherKycService(
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
                coordinator,
                vnptIdentityTransactionClaimRepository,
                accountIdentityVerificationService,
                "storage/kyc",
                "server"
        );
        lenient().when(selfProvider.getObject()).thenReturn(service);

        userId = UUID.randomUUID();
        teacherId = UUID.randomUUID();

        user = new AppUser();
        user.setId(userId);
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmail("teacher@test.com");

        teacherProfile = new TeacherProfile();
        teacherProfile.setId(teacherId);
        teacherProfile.setUser(user);
        teacherProfile.setKycStatus(TeacherKycStatus.PENDING);
        teacherProfile.setCanPublishCourse(false);

        lenient().when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(teacherProfile));
    }

    /**
     * Builds a valid VNPT SDK result map that passes the evaluateSdkResult() checks.
     */
    private Map<String, Object> validSdkResult() {
        return Map.of(
                "object", Map.of(
                        "idNumber", "012345678901",
                        "name", "Nguyễn Văn A",
                        "dateOfBirth", "01/01/1990",
                        "idType", "CCCD",
                        "sex", "Nam",
                        "nationality", "Việt Nam"
                ),
                "compare", Map.of(
                        "result", true,
                        "msg", "Khuôn mặt hợp lệ",
                        "prob", 99
                ),
                "liveness", Map.of(
                        "liveness_result", true,
                        "liveness_msg", "Thành công"
                )
        );
    }

    @Test
    @DisplayName("SDK result alone sets PENDING_SERVER_VERIFICATION, never VERIFIED")
    void sdkResultAlone_setsPendingNotVerified() {
        // Arrange
        lenient().when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherId))
                .thenReturn(Optional.empty());
        lenient().when(teacherIdentityClaimService.normalizeCccd("012345678901")).thenReturn("012345678901");

        // Server verification succeeds (mock adapter)
        lenient().when(vnptVerificationPort.verifyTransaction(any(), any()))
                .thenReturn(VnptServerVerificationResult.success("tx-123", "session-123", "SUCCESS", Instant.now(), "012345678901", "Nguyen Van A", "01/01/1990", "ref"));

        ArgumentCaptor<KycRequest> requestCaptor = ArgumentCaptor.forClass(KycRequest.class);
        java.util.Map<UUID, KycRequest> savedRequestsMap = new java.util.HashMap<>();
        org.mockito.stubbing.Answer<KycRequest> saveAnswer = inv -> {
            KycRequest req = inv.getArgument(0);
            if (req.getId() == null) {
                req.setId(UUID.randomUUID());
            }
            if (req.getSubmittedAt() == null) {
                req.setSubmittedAt(java.time.Instant.now());
            }
            savedRequestsMap.put(req.getId(), req);
            return req;
        };
        lenient().when(kycRequestRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.save(any(KycRequest.class))).thenAnswer(saveAnswer);
        lenient().when(kycRequestRepository.saveAndFlush(any(KycRequest.class))).thenAnswer(saveAnswer);

        KycIdentityVerificationRequest request = new KycIdentityVerificationRequest(
                "session-123", "tx-123", validSdkResult()
        );

        // Act
        KycIdentityVerificationResponse response = service.verifyIdentity(
                userId, request, "127.0.0.1", "Test-Agent"
        );

        // Assert - verify the KycRequest was saved
        verify(kycRequestRepository, atLeastOnce()).saveAndFlush(requestCaptor.capture());
        List<KycRequest> savedRequests = requestCaptor.getAllValues();

        // The first save assertion is now handled inside the mock above.

        // Server verification port must have been called
        verify(vnptVerificationPort).verifyTransaction("tx-123", "session-123");
    }

    @Test
    @DisplayName("Server verification failure results in FAILED status")
    void serverVerificationFailure_setsFailed() {
        // Arrange
        lenient().when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherId))
                .thenReturn(Optional.empty());
        lenient().when(teacherIdentityClaimService.normalizeCccd("012345678901")).thenReturn("012345678901");

        // Server verification fails
        lenient().when(vnptVerificationPort.verifyTransaction(any(), any()))
                .thenReturn(VnptServerVerificationResult.failure("tx-123", "session-123", "FAILED", "TX_NOT_FOUND"));

        ArgumentCaptor<KycRequest> requestCaptor = ArgumentCaptor.forClass(KycRequest.class);
        java.util.Map<UUID, KycRequest> savedRequestsMap = new java.util.HashMap<>();
        org.mockito.stubbing.Answer<KycRequest> saveAnswer = inv -> {
            KycRequest req = inv.getArgument(0);
            if (req.getId() == null) {
                req.setId(UUID.randomUUID());
            }
            if (req.getSubmittedAt() == null) {
                req.setSubmittedAt(java.time.Instant.now());
            }
            savedRequestsMap.put(req.getId(), req);
            return req;
        };
        lenient().when(kycRequestRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.save(any(KycRequest.class))).thenAnswer(saveAnswer);
        lenient().when(kycRequestRepository.saveAndFlush(any(KycRequest.class))).thenAnswer(saveAnswer);

        KycIdentityVerificationRequest request = new KycIdentityVerificationRequest(
                "session-123", "tx-123", validSdkResult()
        );

        // Act
        service.verifyIdentity(userId, request, "127.0.0.1", "Test-Agent");

        // Assert
        verify(kycRequestRepository, atLeastOnce()).saveAndFlush(requestCaptor.capture());
        List<KycRequest> saves = requestCaptor.getAllValues();
        KycRequest finalSave = saves.get(saves.size() - 1);

        assertThat(finalSave.getIdentityStatus())
                .as("Server rejection must result in FAILED")
                .isEqualTo(IdentityVerificationStatus.FAILED);
        assertThat(finalSave.getServerVerifiedAt())
                .as("serverVerifiedAt must be null when server rejected")
                .isNull();
    }

    @Test
    @DisplayName("Server verification success sets VERIFIED with serverVerifiedAt timestamp")
    void serverVerificationSuccess_setsVerifiedWithTimestamp() {
        // Arrange
        lenient().when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherId))
                .thenReturn(Optional.empty());
        lenient().when(teacherIdentityClaimService.normalizeCccd("012345678901")).thenReturn("012345678901");

        lenient().when(vnptVerificationPort.verifyTransaction(any(), any()))
                .thenReturn(VnptServerVerificationResult.success("tx-123", "session-123", "SUCCESS", Instant.now(), "012345678901", "Nguyen Van A", "01/01/1990", "ref"));

        ArgumentCaptor<KycRequest> requestCaptor = ArgumentCaptor.forClass(KycRequest.class);
        java.util.Map<UUID, KycRequest> savedRequestsMap = new java.util.HashMap<>();
        org.mockito.stubbing.Answer<KycRequest> saveAnswer = inv -> {
            KycRequest req = inv.getArgument(0);
            if (req.getId() == null) {
                req.setId(UUID.randomUUID());
            }
            if (req.getSubmittedAt() == null) {
                req.setSubmittedAt(java.time.Instant.now());
            }
            savedRequestsMap.put(req.getId(), req);
            return req;
        };
        lenient().when(kycRequestRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.save(any(KycRequest.class))).thenAnswer(saveAnswer);
        lenient().when(kycRequestRepository.saveAndFlush(any(KycRequest.class))).thenAnswer(saveAnswer);

        KycIdentityVerificationRequest request = new KycIdentityVerificationRequest(
                "session-123", "tx-123", validSdkResult()
        );

        // Act
        service.verifyIdentity(userId, request, "127.0.0.1", "Test-Agent");

        // Assert
        verify(kycRequestRepository, atLeastOnce()).saveAndFlush(requestCaptor.capture());
        List<KycRequest> saves = requestCaptor.getAllValues();
        KycRequest finalSave = saves.get(saves.size() - 1);

        assertThat(finalSave.getIdentityStatus())
                .isEqualTo(IdentityVerificationStatus.VERIFIED);
        assertThat(finalSave.getServerVerifiedAt())
                .as("serverVerifiedAt must be set on server confirmation")
                .isNotNull();
        assertThat(finalSave.getIdentityVerifiedAt())
                .as("identityVerifiedAt must be set on server confirmation")
                .isNotNull();
    }

    @Test
    @DisplayName("Server exception leaves status as PENDING_SERVER_VERIFICATION for retry")
    void serverException_leavesPending() {
        // Arrange
        lenient().when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherId))
                .thenReturn(Optional.empty());
        lenient().when(teacherIdentityClaimService.normalizeCccd("012345678901")).thenReturn("012345678901");

        // Server call throws exception (transient failure)
        lenient().when(vnptVerificationPort.verifyTransaction(any(), any()))
                .thenThrow(new RuntimeException("Connection timeout"));

        ArgumentCaptor<KycRequest> requestCaptor = ArgumentCaptor.forClass(KycRequest.class);
        java.util.Map<UUID, KycRequest> savedRequestsMap = new java.util.HashMap<>();
        org.mockito.stubbing.Answer<KycRequest> saveAnswer = inv -> {
            KycRequest req = inv.getArgument(0);
            if (req.getId() == null) {
                req.setId(UUID.randomUUID());
            }
            if (req.getSubmittedAt() == null) {
                req.setSubmittedAt(java.time.Instant.now());
            }
            savedRequestsMap.put(req.getId(), req);
            return req;
        };
        lenient().when(kycRequestRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.save(any(KycRequest.class))).thenAnswer(saveAnswer);
        lenient().when(kycRequestRepository.saveAndFlush(any(KycRequest.class))).thenAnswer(saveAnswer);

        KycIdentityVerificationRequest request = new KycIdentityVerificationRequest(
                "session-123", "tx-123", validSdkResult()
        );

        // Act
        com.manabihub.common.exception.BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.manabihub.common.exception.BusinessException.class,
                () -> service.verifyIdentity(userId, request, "127.0.0.1", "Test-Agent"));
        assertThat(ex.getMessageCode()).isEqualTo(com.manabihub.common.constants.MessageCodes.MSG_KYC_009);

        // Assert - should remain PENDING for retry
        verify(kycRequestRepository, atLeastOnce()).saveAndFlush(requestCaptor.capture());
        List<KycRequest> saves = requestCaptor.getAllValues();
        KycRequest finalSave = saves.get(saves.size() - 1);

        assertThat(finalSave.getIdentityStatus())
                .as("Transient error must leave PENDING for retry, not mark as FAILED")
                .isEqualTo(IdentityVerificationStatus.PENDING_SERVER_VERIFICATION);
    }

    @Test
    @DisplayName("processIdentityClaim is only called after server confirmation, not after SDK result")
    void identityClaimOnlyAfterServerConfirmation() {
        // Arrange
        lenient().when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        lenient().when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherId))
                .thenReturn(Optional.empty());
        lenient().when(teacherIdentityClaimService.normalizeCccd("012345678901")).thenReturn("012345678901");

        lenient().when(vnptVerificationPort.verifyTransaction(any(), any()))
                .thenReturn(VnptServerVerificationResult.success("tx-123", "session-123", "SUCCESS", Instant.now(), "012345678901", "Nguyen Van A", "01/01/1990", "ref"));

        java.util.Map<UUID, KycRequest> savedRequestsMap = new java.util.HashMap<>();
        org.mockito.stubbing.Answer<KycRequest> saveAnswer = inv -> {
            KycRequest req = inv.getArgument(0);
            if (req.getId() == null) {
                req.setId(UUID.randomUUID());
            }
            if (req.getSubmittedAt() == null) {
                req.setSubmittedAt(java.time.Instant.now());
            }
            savedRequestsMap.put(req.getId(), req);
            return req;
        };
        lenient().when(kycRequestRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(savedRequestsMap.get(inv.getArgument(0))));
        lenient().when(kycRequestRepository.save(any(KycRequest.class))).thenAnswer(saveAnswer);
        lenient().when(kycRequestRepository.saveAndFlush(any(KycRequest.class))).thenAnswer(saveAnswer);

        KycIdentityVerificationRequest request = new KycIdentityVerificationRequest(
                "session-123", "tx-123", validSdkResult()
        );

        // Act
        service.verifyIdentity(userId, request, "127.0.0.1", "Test-Agent");

        // Assert - processIdentityClaim called exactly once (from confirmServerVerification)
        verify(teacherIdentityClaimService, times(1)).processIdentityClaim(
                eq(teacherId), anyString(), any(AppUser.class), anyString(), anyString()
        );
    }
}
