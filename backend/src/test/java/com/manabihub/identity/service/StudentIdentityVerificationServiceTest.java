package com.manabihub.identity.service;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.identity.dto.request.StudentIdentityVerificationRequest;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.entity.AccountIdentityVerification;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.kyc.domain.VnptIdentityTransactionClaim;
import com.manabihub.kyc.repository.VnptIdentityTransactionClaimRepository;
import com.manabihub.identity.service.impl.StudentIdentityVerificationServiceImpl;
import com.manabihub.kyc.port.NationalIdRecordDto;
import com.manabihub.kyc.port.NationalIdRegistryPort;
import com.manabihub.kyc.port.VnptServerVerificationResult;
import com.manabihub.kyc.port.VnptVerificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentIdentityVerificationServiceTest {

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private NationalIdRegistryPort nationalIdRegistry;

    @Mock
    private VnptVerificationPort vnptVerificationPort;

    @Mock
    private VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository;

    @Mock
    private DatabaseAuthRateLimiter vnptKycRateLimiter;

    @Mock
    private AccountIdentityVerificationService accountIdentityVerificationService;

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private StudentIdentityVerificationServiceImpl service;

    private StudentProfile student;
    private UUID userId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "identitySecret", "student-identity-test-secret-32-bytes");
        ReflectionTestUtils.setField(service, "identityVerificationMode", "direct-sdk-mock");
        student = new StudentProfile();
        student.setId(UUID.randomUUID());
        userId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        lenient().when(studentProfileRepository.findByUser_Id(any())).thenReturn(Optional.of(student));
        lenient().when(vnptKycRateLimiter.consume(any(), any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(true);
    }

    @Test
    void getStatus_usesIdentityVerifiedThroughTeacherEntryPoint() {
        AccountIdentityVerification shared = new AccountIdentityVerification();
        shared.setUserId(userId);
        shared.setIdentityFingerprint("fingerprint");
        shared.setProvider("VNPT_EKYC_WEB_SDK");
        shared.setFullName("NGUYEN VAN A");
        shared.setDateOfBirth(LocalDate.of(1990, 1, 2));
        shared.setVerifiedAt(Instant.parse("2026-08-14T00:00:00Z"));
        shared.setSourceSubject("TEACHER");
        when(accountIdentityVerificationService.findVerified(userId)).thenReturn(Optional.of(shared));

        var response = service.getStatus();

        assertTrue(response.verified());
        assertEquals("NGUYEN VAN A", response.fullName());
        assertEquals("VNPT_EKYC_WEB_SDK", response.provider());
        verify(studentProfileRepository, never()).findByUser_Id(any());
    }

    @Test
    void verify_matchesNestedVnptPayloadAgainstSyntheticRegistry() {
        when(nationalIdRegistry.findActiveByIdNumber("027204002711"))
                .thenReturn(Optional.of(new NationalIdRecordDto(
                        "027204002711", "NGUYEN XUAN DAT", LocalDate.of(2004, 8, 31))));
        when(studentProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-1",
                "tx-1",
                Map.of("result", Map.of(
                        "idNumber", "027 204 002 711",
                        "fullName", "Nguyễn Xuân Đạt",
                        "dateOfBirth", "31/08/2004"),
                "liveness_face", Map.of("liveness", "success"),
                "compare", Map.of("result", "match", "prob", 0.98D),
                "masked", Map.of("masked", "false")));

        var response = service.verify(request);

        assertEquals("VERIFIED", response.status());
        assertEquals("NGUYEN XUAN DAT", response.fullName());
        assertNotNull(student.getIdentityFingerprint());
        assertNotNull(student.getIdentityVerifiedAt());
    }

    @Test
    void verify_rejectsPayloadThatDoesNotMatchSyntheticRegistry() {
        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                null,
                null,
                Map.of("idNumber", "027204002711", "fullName", "SOMEONE ELSE", "dob", "2004-08-31"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify(request));

        assertEquals("MSG-KYC-002", error.getMessageCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    }

    @Test
    void verify_duplicateCccdReturnsIdv002AndRecordsSecurityAudit() {
        when(nationalIdRegistry.findActiveByIdNumber("027204002711"))
                .thenReturn(Optional.of(new NationalIdRecordDto(
                        "027204002711", "NGUYEN XUAN DAT", LocalDate.of(2004, 8, 31))));
        when(accountIdentityVerificationService.recordVerified(
                any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(
                        MessageCodes.MSG_KYC_008,
                        "This CCCD is already linked to another account",
                        HttpStatus.CONFLICT));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify(
                new StudentIdentityVerificationRequest(
                        null,
                        null,
                        validSdkResult("027204002711", "NGUYEN XUAN DAT", "2004-08-31"))));

        assertEquals(MessageCodes.MSG_IDV_002, error.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
        verify(securityAuditService).logStudentDuplicateIdentityAudit(student.getId(), userId);
        verify(studentProfileRepository, never()).save(any());
    }

    @Test
    void verify_directSdkMarksSandboxIdentityVerifiedAndBindsTransaction() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "direct-sdk");
        when(studentProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-uat-1",
                "tx-uat-1",
                validSdkResult("027204002711", "NGUYEN XUAN DAT", "2004-08-31"));

        var response = service.verify(request);

        assertTrue(response.verified());
        assertEquals("VERIFIED", response.status());
        assertEquals("VNPT_EKYC_WEB_SDK_UAT", response.provider());
        assertEquals("NGUYEN XUAN DAT", response.fullName());
        assertNotNull(response.verifiedAt());
        assertNotNull(student.getIdentityVerifiedAt());
        assertNotNull(student.getIdentityFingerprint());
        verify(vnptVerificationPort, never()).verifyTransaction(any(), any());
        verify(nationalIdRegistry, never()).findActiveByIdNumber(any());
        verify(vnptIdentityTransactionClaimRepository).saveAndFlush(argThat(claim ->
                userId.equals(claim.getUserId())
                        && "STUDENT".equals(claim.getSubjectType())
                        && "VNPT_EKYC_WEB_SDK".equals(claim.getProvider())
                        && "tx-uat-1".equals(claim.getProviderTransactionId())
                        && "session-uat-1".equals(claim.getProviderSessionId())));
    }

    @Test
    void verify_directSdkUsesSessionReplayBindingWhenTransactionIsMissing() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "direct-sdk");
        when(studentProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-uat-2",
                null,
                validSdkResult("027204002711", "NGUYEN XUAN DAT", "2004-08-31"));

        var response = service.verify(request);

        assertEquals("VERIFIED", response.status());
        verify(vnptIdentityTransactionClaimRepository).saveAndFlush(argThat(claim ->
                "SESSION:session-uat-2".equals(claim.getProviderTransactionId())
                        && "session-uat-2".equals(claim.getProviderSessionId())));
        verify(vnptVerificationPort, never()).verifyTransaction(any(), any());
    }

    @Test
    void verify_directSdkRequiresProviderSessionId() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "direct-sdk");
        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                null,
                "tx-uat-1",
                validSdkResult("027204002711", "NGUYEN XUAN DAT", "2004-08-31"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify(request));

        assertEquals("MSG-KYC-002", error.getMessageCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verify(vnptKycRateLimiter, never()).consume(any(), any(), any(), anyInt(), anyInt(), anyInt());
        verify(vnptIdentityTransactionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    void verify_directSdkRateLimitsBrowserEvaluations() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "direct-sdk");
        when(vnptKycRateLimiter.consume(any(), any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(false);
        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-uat-1",
                null,
                validSdkResult("027204002711", "NGUYEN XUAN DAT", "2004-08-31"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify(request));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getHttpStatus());
        verify(vnptVerificationPort, never()).verifyTransaction(any(), any());
        verify(vnptIdentityTransactionClaimRepository, never()).saveAndFlush(any());
        verify(studentProfileRepository, never()).save(any());
    }

    @Test
    void verify_directSdkRejectsReplayClaimedByAnotherAccount() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "direct-sdk");
        VnptIdentityTransactionClaim existingClaim = new VnptIdentityTransactionClaim();
        existingClaim.setUserId(UUID.randomUUID());
        existingClaim.setSubjectType("STUDENT");
        existingClaim.setProviderSessionId("session-uat-1");
        when(vnptIdentityTransactionClaimRepository.findByProviderAndProviderTransactionId(
                "VNPT_EKYC_WEB_SDK", "SESSION:session-uat-1"))
                .thenReturn(Optional.of(existingClaim));
        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-uat-1",
                null,
                validSdkResult("027204002711", "NGUYEN XUAN DAT", "2004-08-31"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify(request));

        assertEquals("MSG-KYC-008", error.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
        verify(vnptVerificationPort, never()).verifyTransaction(any(), any());
        verify(studentProfileRepository, never()).save(any());
    }

    @Test
    void verify_serverModeUsesServerIdentityAndBindsConfirmedProviderIds() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "server");
        when(vnptVerificationPort.verifyTransaction("tx-1", "session-1"))
                .thenReturn(VnptServerVerificationResult.success(
                        "tx-1",
                        "session-1",
                        "VERIFIED",
                        Instant.now(),
                        "027204002711",
                        "NGUYEN XUAN DAT",
                        "2004-08-31",
                        "********2711"));
        when(studentProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-1",
                "tx-1",
                validSdkResult("999999999999", "FORGED BROWSER NAME", "1990-01-01"));

        var response = service.verify(request);

        assertEquals("VERIFIED", response.status());
        assertEquals("VNPT_EKYC_WEB_SDK", response.provider());
        assertEquals("NGUYEN XUAN DAT", response.fullName());
        verify(vnptVerificationPort).verifyTransaction("tx-1", "session-1");
        verify(vnptIdentityTransactionClaimRepository).saveAndFlush(any());
        verify(nationalIdRegistry, never()).findActiveByIdNumber(any());
    }

    @Test
    void verify_serverModeRejectsMismatchedServerBinding() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "server");
        when(vnptVerificationPort.verifyTransaction("tx-1", "session-1"))
                .thenReturn(VnptServerVerificationResult.success(
                        "different-tx",
                        "session-1",
                        "VERIFIED",
                        Instant.now(),
                        "027204002711",
                        "NGUYEN XUAN DAT",
                        "2004-08-31",
                        "********2711"));

        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-1",
                "tx-1",
                validSdkResult("027204002711", "NGUYEN XUAN DAT", "2004-08-31"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify(request));

        assertEquals("MSG-KYC-002", error.getMessageCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verify(studentProfileRepository, never()).save(any());
    }

    @Test
    void verify_serverModeRejectsAReplayedProviderTransaction() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "server");
        when(vnptVerificationPort.verifyTransaction("tx-1", "session-1"))
                .thenReturn(VnptServerVerificationResult.success(
                        "tx-1", "session-1", "VERIFIED", Instant.now(),
                        "027204002711", "NGUYEN XUAN DAT", "2004-08-31", "********2711"));
        when(vnptIdentityTransactionClaimRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate provider transaction"));

        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-1", "tx-1",
                validSdkResult("027204002711", "NGUYEN XUAN DAT", "2004-08-31"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify(request));

        assertEquals("MSG-KYC-008", error.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
        verify(studentProfileRepository, never()).save(any());
    }

    @Test
    void verify_serverModeRateLimitsProviderCallsPerAccount() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "server");
        when(vnptKycRateLimiter.consume(any(), any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(false);
        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-1", "tx-1",
                validSdkResult("027204002711", "NGUYEN XUAN DAT", "2004-08-31"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify(request));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getHttpStatus());
        verify(vnptVerificationPort, never()).verifyTransaction(any(), any());
    }

    @Test
    void verify_terminalCancellationOverridesEarlierSuccessAndSkipsServerCall() {
        ReflectionTestUtils.setField(service, "identityVerificationMode", "server");
        StudentIdentityVerificationRequest request = new StudentIdentityVerificationRequest(
                "session-1",
                "tx-1",
                Map.of(
                        "documentResult", Map.of(
                                "idNumber", "027204002711",
                                "fullName", "NGUYEN XUAN DAT",
                                "dateOfBirth", "2004-08-31"),
                        "callbackResult", Map.of(
                                "faceLiveness", Map.of("status", "SUCCESS"),
                                "faceCompare", Map.of("result", "MATCH")),
                        "endFlowResult", Map.of("status", "CANCELLED")));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify(request));

        assertEquals("MSG-KYC-002", error.getMessageCode());
        verify(vnptVerificationPort, never()).verifyTransaction(any(), any());
        verify(studentProfileRepository, never()).save(any());
    }

    private Map<String, Object> validSdkResult(String idNumber, String fullName, String dateOfBirth) {
        return Map.of(
                "documentResult", Map.of(
                        "idNumber", idNumber,
                        "fullName", fullName,
                        "dateOfBirth", dateOfBirth),
                "callbackResult", Map.of(
                        "faceLiveness", Map.of("status", "SUCCESS"),
                        "faceCompare", Map.of("result", "MATCH")));
    }
}
