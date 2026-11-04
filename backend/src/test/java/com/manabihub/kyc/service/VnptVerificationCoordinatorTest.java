package com.manabihub.kyc.service;

import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.*;
import com.manabihub.kyc.dto.KycIdentityVerificationRequest;
import com.manabihub.kyc.port.VnptVerificationPort;
import com.manabihub.kyc.port.VnptServerVerificationResult;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.kyc.repository.VnptIdentityTransactionClaimRepository;
import com.manabihub.kyc.service.VnptVerificationCoordinator.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VnptVerificationCoordinatorTest {

    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private KycRequestRepository kycRequestRepository;
    @Mock private VnptVerificationPort vnptVerificationPort;
    @Mock private TeacherIdentityClaimService teacherIdentityClaimService;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository;
    @Mock private ObjectProvider<VnptVerificationCoordinator> selfProvider;

    private final Instant fixedNow = Instant.parse("2026-01-01T10:00:00Z");
    private Clock clock;
    private VnptVerificationCoordinator coordinator;
    private UUID userId;
    private TeacherProfile teacherProfile;
    private AppUser user;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));
        coordinator = new VnptVerificationCoordinator(
                teacherProfileRepository, kycRequestRepository, vnptVerificationPort,
                teacherIdentityClaimService, securityAuditService,
                vnptIdentityTransactionClaimRepository, clock, selfProvider
        );
        userId = UUID.randomUUID();
        user = new AppUser();
        user.setId(userId);
        user.setUserStatus(UserStatus.ACTIVE);

        teacherProfile = new TeacherProfile();
        teacherProfile.setId(UUID.randomUUID());
        teacherProfile.setUser(user);
        teacherProfile.setKycStatus(TeacherKycStatus.NOT_SUBMITTED);
    }

    private VnptSdkDecision passSdk() {
        return new VnptSdkDecision(true, Map.of(), List.of());
    }

    private VnptSdkDecision failSdk() {
        return new VnptSdkDecision(false, Map.of(), List.of("LIVENESS_FAILED"));
    }

    private KycIdentityVerificationRequest req(String session, String tx) {
        return new KycIdentityVerificationRequest(session, tx, Map.of());
    }

    private KycRequest draftRequest(String tx, String session) {
        KycRequest r = new KycRequest();
        r.setId(UUID.randomUUID());
        r.setTeacherProfile(teacherProfile);
        r.setProviderTransactionId(tx);
        r.setProviderSessionId(session);
        r.setStatus(KycRequestStatus.DRAFT);
        r.setIdentityStatus(IdentityVerificationStatus.PENDING_SERVER_VERIFICATION);
        r.setServerVerificationExpiresAt(fixedNow.plusSeconds(1800));
        r.setServerVerificationAttemptCount(1);
        return r;
    }

    // ------------------------------------------------------------------
    // Existing tests
    // ------------------------------------------------------------------

    @Test
    void bindVerificationAttempt_failsIfUserInactive() {
        teacherProfile.getUser().setUserStatus(UserStatus.LOCKED);
        when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                coordinator.bindVerificationAttempt(userId, req("s", "t"), passSdk(), "127.0.0.1", "agent")
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    void applyProviderResult_savesSuccessAndClaimsIdentity() {
        KycRequest request = draftRequest("tx", "session");
        request.setSubmittedAt(fixedNow.minusSeconds(60));

        VnptServerVerificationResult serverResult = VnptServerVerificationResult.success(
                "tx", "session", "SUCCESS", fixedNow, "012345678901", "Nguyen Van A", "1990-01-01", "ref"
        );

        when(kycRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(teacherIdentityClaimService.normalizeCccd("012345678901")).thenReturn("012345678901");

        ApplyResult result = coordinator.applyProviderResult(request.getId(), userId, "127.0.0.1", "agent", serverResult);

        assertEquals(IdentityVerificationStatus.VERIFIED, result.finalStatus());
        assertTrue(result.claimProcessed());
        assertEquals("Nguyen Van A", request.getServerFullName());
        verify(kycRequestRepository).saveAndFlush(request);
        verify(teacherIdentityClaimService).processIdentityClaim(eq(teacherProfile.getId()), eq("012345678901"), any(), eq("127.0.0.1"), eq("agent"));
    }

    @Test
    void orchestrate_delegatesToBindAndApply() {
        KycRequest mockRequest = draftRequest("tx", "session");
        mockRequest.setSubmittedAt(fixedNow.minusSeconds(60));

        when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())).thenReturn(Optional.empty());
        when(kycRequestRepository.existsByEkycProviderAndProviderTransactionId(anyString(), anyString())).thenReturn(false);
        when(kycRequestRepository.saveAndFlush(any())).thenReturn(mockRequest);
        when(selfProvider.getObject()).thenReturn(coordinator);

        VnptServerVerificationResult serverResult = VnptServerVerificationResult.success(
                "tx", "session", "SUCCESS", fixedNow, "012345678901", "Nguyen Van A", "1990-01-01", "ref"
        );
        when(vnptVerificationPort.verifyTransaction(anyString(), anyString())).thenReturn(serverResult);
        when(kycRequestRepository.findByIdForUpdate(any())).thenReturn(Optional.of(mockRequest));
        when(teacherIdentityClaimService.normalizeCccd("012345678901")).thenReturn("012345678901");

        VerificationOutcome outcome = coordinator.orchestrate(
                userId, req("session", "tx"), passSdk(), "127.0.0.1", "agent"
        );

        assertEquals(IdentityVerificationStatus.VERIFIED, outcome.finalStatus());
    }

    // ------------------------------------------------------------------
    // Item 3: CONFLICT maps to 409
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CONFLICT bind status throws BusinessException 409")
    void conflict_mapsTo409() {
        // Set up a DRAFT request with a different tx so we get CONFLICT
        KycRequest existing = draftRequest("other-tx", "other-session");
        existing.setIdentityStatus(IdentityVerificationStatus.PENDING_SERVER_VERIFICATION);

        when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())).thenReturn(Optional.of(existing));
        when(selfProvider.getObject()).thenReturn(coordinator);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                coordinator.orchestrate(userId, req("new-session", "new-tx"), passSdk(), "127.0.0.1", "agent")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals(MessageCodes.MSG_KYC_008, ex.getMessageCode());
    }

    // ------------------------------------------------------------------
    // Item 4: fail-closed maps PROVIDER_NOT_CONFIGURED audit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fail-closed adapter triggers PROVIDER_NOT_CONFIGURED audit via reasonCode")
    void failClosed_mapsProviderNotConfiguredAudit() {
        KycRequest request = draftRequest("tx", "session");
        request.setSubmittedAt(fixedNow.minusSeconds(60));

        VnptServerVerificationResult failClosedResult = VnptServerVerificationResult.failure(
                "tx", "session", "NOT_CONFIGURED", "PROVIDER_NOT_CONFIGURED"
        );

        when(kycRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        ApplyResult result = coordinator.applyProviderResult(request.getId(), userId, "127.0.0.1", "agent", failClosedResult);

        assertEquals(IdentityVerificationStatus.FAILED, result.finalStatus());
        verify(securityAuditService).logVerificationEvent(eq("PROVIDER_NOT_CONFIGURED"),
                eq(teacherProfile.getId()), eq(request.getId()), eq(userId), eq("127.0.0.1"), eq("agent"));
    }

    // ------------------------------------------------------------------
    // Item 4 continued: other failures audit PROVIDER_REJECTED
    // ------------------------------------------------------------------

    @Test
    @DisplayName("non-configured failure audits PROVIDER_REJECTED")
    void otherFailure_auditsProviderRejected() {
        KycRequest request = draftRequest("tx", "session");
        request.setSubmittedAt(fixedNow.minusSeconds(60));

        VnptServerVerificationResult rejected = VnptServerVerificationResult.failure(
                "tx", "session", "REJECTED", "TX_NOT_FOUND"
        );

        when(kycRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        coordinator.applyProviderResult(request.getId(), userId, "127.0.0.1", "agent", rejected);

        verify(securityAuditService).logVerificationEvent(eq("PROVIDER_REJECTED"),
                eq(teacherProfile.getId()), eq(request.getId()), eq(userId), eq("127.0.0.1"), eq("agent"));
    }

    // ------------------------------------------------------------------
    // Null provider result persists FAILED
    // ------------------------------------------------------------------

    @Test
    @DisplayName("null provider result persists FAILED status")
    void nullProviderResult_persistsFailed() {
        KycRequest request = draftRequest("tx", "session");
        request.setSubmittedAt(fixedNow.minusSeconds(60));

        when(kycRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        ApplyResult result = coordinator.applyProviderResult(request.getId(), userId, "127.0.0.1", "agent", null);

        assertEquals(IdentityVerificationStatus.FAILED, result.finalStatus());
        assertEquals(IdentityVerificationStatus.FAILED, request.getIdentityStatus());
        verify(kycRequestRepository).saveAndFlush(request);
    }

    // ------------------------------------------------------------------
    // Item 6: now equal/after expiry fails
    // ------------------------------------------------------------------

    @Test
    @DisplayName("applyProviderResult rejects when now >= serverVerificationExpiresAt")
    void nowEqualExpiry_fails() {
        KycRequest request = draftRequest("tx", "session");
        request.setSubmittedAt(fixedNow.minusSeconds(60));
        request.setServerVerificationExpiresAt(fixedNow); // expires exactly at now

        when(kycRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        VnptServerVerificationResult serverResult = VnptServerVerificationResult.success(
                "tx", "session", "SUCCESS", fixedNow.minusSeconds(1), "012345678901", "A", "1990-01-01", "ref"
        );

        ApplyResult result = coordinator.applyProviderResult(request.getId(), userId, "127.0.0.1", "agent", serverResult);

        assertEquals(IdentityVerificationStatus.FAILED, result.finalStatus());
        verify(securityAuditService).logVerificationEvent(eq("EXPIRED"),
                eq(teacherProfile.getId()), eq(request.getId()), eq(userId), eq("127.0.0.1"), eq("agent"));
    }

    // ------------------------------------------------------------------
    // Item 6: stale relative to submittedAt fails
    // ------------------------------------------------------------------

    @Test
    @DisplayName("providerVerifiedAt before submittedAt - CLOCK_SKEW_TOLERANCE is stale")
    void staleRelativeToSubmittedAt_fails() {
        KycRequest request = draftRequest("tx", "session");
        request.setSubmittedAt(fixedNow.minusSeconds(60));
        // verifiedAt = submittedAt - 6 minutes (beyond 5-min tolerance)
        Instant staleVerifiedAt = request.getSubmittedAt().minusSeconds(360);

        when(kycRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        VnptServerVerificationResult serverResult = VnptServerVerificationResult.success(
                "tx", "session", "SUCCESS", staleVerifiedAt, "012345678901", "A", "1990-01-01", "ref"
        );

        ApplyResult result = coordinator.applyProviderResult(request.getId(), userId, "127.0.0.1", "agent", serverResult);

        assertEquals(IdentityVerificationStatus.FAILED, result.finalStatus());
        verify(securityAuditService).logVerificationEvent(eq("STALE_TIMESTAMP"),
                eq(teacherProfile.getId()), eq(request.getId()), eq(userId), eq("127.0.0.1"), eq("agent"));
    }

    // ------------------------------------------------------------------
    // Third timeout persists FAILED/MAX_ATTEMPTS
    // ------------------------------------------------------------------

    @Test
    @DisplayName("third timeout persists FAILED with MAX_ATTEMPTS audit")
    void thirdTimeout_persistsFailedMaxAttempts() {
        KycRequest request = draftRequest("tx", "session");
        request.setServerVerificationAttemptCount(3);

        when(kycRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        VerificationOutcome outcome = coordinator.recordProviderTimeout(request.getId(), userId, "127.0.0.1", "agent");

        assertEquals(IdentityVerificationStatus.FAILED, outcome.finalStatus());
        verify(securityAuditService).logVerificationEvent(eq("MAX_ATTEMPTS"),
                eq(teacherProfile.getId()), eq(request.getId()), eq(userId), eq("127.0.0.1"), eq("agent"));
        verify(kycRequestRepository).saveAndFlush(request);
    }


    @Test
    @DisplayName("KS04: provider not configured -> MSG-KYC-009 (503), attempt stays retryable")
    void orchestrate_providerNotConfigured_throwsKyc009AndStaysPending() {
        KycRequest mockRequest = stubNewAttempt();
        when(vnptVerificationPort.verifyTransaction(anyString(), anyString())).thenReturn(
                VnptServerVerificationResult.failure("tx", "session", "NOT_CONFIGURED", "PROVIDER_NOT_CONFIGURED"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                coordinator.orchestrate(userId, req("session", "tx"), passSdk(), "127.0.0.1", "agent"));

        assertEquals(MessageCodes.MSG_KYC_009, ex.getMessageCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getHttpStatus());
        assertEquals(IdentityVerificationStatus.PENDING_SERVER_VERIFICATION, mockRequest.getIdentityStatus());
        verify(securityAuditService).logVerificationEvent(eq("PROVIDER_NOT_CONFIGURED"),
                eq(teacherProfile.getId()), eq(mockRequest.getId()), eq(userId), eq("127.0.0.1"), eq("agent"));
    }

    @Test
    @DisplayName("KS04: provider timeout -> MSG-KYC-009 (503), attempt stays retryable")
    void orchestrate_providerTimeout_throwsKyc009AndStaysPending() {
        KycRequest mockRequest = stubNewAttempt();
        when(vnptVerificationPort.verifyTransaction(anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection timeout"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                coordinator.orchestrate(userId, req("session", "tx"), passSdk(), "127.0.0.1", "agent"));

        assertEquals(MessageCodes.MSG_KYC_009, ex.getMessageCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getHttpStatus());
        assertEquals(IdentityVerificationStatus.PENDING_SERVER_VERIFICATION, mockRequest.getIdentityStatus());
        verify(securityAuditService).logVerificationEvent(eq("PROVIDER_TIMEOUT"),
                eq(teacherProfile.getId()), eq(mockRequest.getId()), eq(userId), eq("127.0.0.1"), eq("agent"));
    }

    private KycRequest stubNewAttempt() {
        KycRequest mockRequest = draftRequest("tx", "session");
        mockRequest.setSubmittedAt(fixedNow.minusSeconds(60));
        when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId()))
                .thenReturn(Optional.empty());
        when(kycRequestRepository.existsByEkycProviderAndProviderTransactionId(anyString(), anyString()))
                .thenReturn(false);
        when(kycRequestRepository.saveAndFlush(any())).thenReturn(mockRequest);
        when(kycRequestRepository.findByIdForUpdate(any())).thenReturn(Optional.of(mockRequest));
        when(selfProvider.getObject()).thenReturn(coordinator);
        return mockRequest;
    }

    // ------------------------------------------------------------------
    // markClaimFailed preserves provider binding
    // ------------------------------------------------------------------

    @Test
    @DisplayName("markClaimFailed preserves provider binding fields")
    void markClaimFailed_preservesProviderBinding() {
        KycRequest request = draftRequest("tx-preserved", "session-preserved");
        request.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        request.setServerFullName("Nguyen Van A");
        request.setServerDateOfBirth("1990-01-01");

        when(kycRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        coordinator.markClaimFailed(request.getId(), userId, "127.0.0.1", "agent");

        assertEquals(IdentityVerificationStatus.FAILED, request.getIdentityStatus());
        assertEquals("tx-preserved", request.getProviderTransactionId());
        assertEquals("session-preserved", request.getProviderSessionId());
        assertEquals("Nguyen Van A", request.getServerFullName());
        verify(kycRequestRepository).saveAndFlush(request);
    }

    // ------------------------------------------------------------------
    // Item 7: PENDING certificate remains LOCKED
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PENDING_SERVER_VERIFICATION bind sets certificateStatus = LOCKED")
    void pendingBind_certificateRemainsLocked() {
        when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())).thenReturn(Optional.empty());
        when(kycRequestRepository.existsByEkycProviderAndProviderTransactionId(anyString(), anyString())).thenReturn(false);
        when(kycRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BindResult result = coordinator.bindVerificationAttempt(userId, req("session", "tx"), passSdk(), "127.0.0.1", "agent");

        assertEquals(BindResult.BindStatus.NEEDS_SERVER_CALL, result.status());
        // Capture the saved request to check certificate status
        verify(kycRequestRepository).saveAndFlush(argThat(r ->
                r.getCertificateStatus() == CertificateVerificationStatus.LOCKED
        ));
    }

    // ------------------------------------------------------------------
    // Item 7: success changes certificate to NOT_SUBMITTED
    // ------------------------------------------------------------------

    @Test
    @DisplayName("provider success changes certificateStatus to NOT_SUBMITTED")
    void providerSuccess_certificateNotSubmitted() {
        KycRequest request = draftRequest("tx", "session");
        request.setSubmittedAt(fixedNow.minusSeconds(60));
        request.setCertificateStatus(CertificateVerificationStatus.LOCKED);

        VnptServerVerificationResult serverResult = VnptServerVerificationResult.success(
                "tx", "session", "SUCCESS", fixedNow, "012345678901", "Nguyen Van A", "1990-01-01", "ref"
        );

        when(kycRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(teacherIdentityClaimService.normalizeCccd("012345678901")).thenReturn("012345678901");

        coordinator.applyProviderResult(request.getId(), userId, "127.0.0.1", "agent", serverResult);

        assertEquals(CertificateVerificationStatus.NOT_SUBMITTED, request.getCertificateStatus());
    }

    // ------------------------------------------------------------------
    // Item 2: DataIntegrityViolationException in orchestrate → 409
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DataIntegrityViolationException with uq_kyc_requests_provider_tx constraint → 409")
    void orchestrate_dataIntegrity_duplicateTx_throws409() {
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "could not execute statement; constraint [uq_kyc_requests_provider_tx]"
        );

        when(teacherProfileRepository.findForUpdateByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())).thenReturn(Optional.empty());
        when(kycRequestRepository.existsByEkycProviderAndProviderTransactionId(anyString(), anyString())).thenReturn(false);
        when(kycRequestRepository.saveAndFlush(any())).thenThrow(dive);

        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(teacherProfile));
        when(selfProvider.getObject()).thenReturn(coordinator);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                coordinator.orchestrate(userId, req("session", "tx"), passSdk(), "127.0.0.1", "agent")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals(MessageCodes.MSG_KYC_008, ex.getMessageCode());
        verify(securityAuditService).logVerificationEvent(eq("DUPLICATE_TRANSACTION"),
                eq(teacherProfile.getId()), any(), eq(userId), eq("127.0.0.1"), eq("agent"));
    }
}
