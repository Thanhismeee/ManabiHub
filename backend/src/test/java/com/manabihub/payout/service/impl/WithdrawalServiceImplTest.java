package com.manabihub.payout.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.AccountIdentityVerification;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.service.AccountIdentityVerificationService;
import com.manabihub.kyc.domain.IdentityVerificationStatus;
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.payout.dto.request.BankAccountDto;
import com.manabihub.payout.dto.request.CreateWithdrawalRequest;
import com.manabihub.payout.dto.response.WithdrawalRequestResponse;
import com.manabihub.payout.entity.TeacherBankAccount;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.payout.mapper.WithdrawalMapper;
import com.manabihub.payout.repository.TeacherBankAccountRepository;
import com.manabihub.payout.repository.WithdrawalRequestRepository;
import com.manabihub.payout.security.PayoutSecurityService;
import com.manabihub.payout.service.WithdrawalOtpService;
import com.manabihub.payout.service.WithdrawalNotificationService;
import com.manabihub.systemconfig.model.CommercialPolicy;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

    @Mock private WithdrawalRequestRepository withdrawalRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private TeacherBankAccountRepository bankAccountRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private KycRequestRepository kycRequestRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private WalletService walletService;
    @Mock private WithdrawalMapper withdrawalMapper;
    @Mock private WithdrawalNotificationService notificationService;
    @Mock private WithdrawalOtpService otpService;
    @Mock private PayoutSecurityService securityService;
    @Mock private CommercialPolicyService commercialPolicyService;
    @Mock private AccountIdentityVerificationService accountIdentityVerificationService;

    @InjectMocks
    private WithdrawalServiceImpl withdrawalService;

    private final String userIdString = "d290f1ee-6c54-4b01-90e6-d701748f0851";
    private final UUID userId = UUID.fromString(userIdString);
    private final UUID teacherProfileId =
            UUID.fromString("b82e8ebf-9997-45a6-bdbe-3fbe6ad25b04");
    private final BigDecimal minimumPayout = new BigDecimal("500000");
    private Wallet wallet;
    private TeacherProfile teacherProfile;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(commercialPolicyService.getCurrentPolicy())
                .thenReturn(policy(minimumPayout));

        teacherProfile = new TeacherProfile();
        teacherProfile.setId(teacherProfileId);
        org.mockito.Mockito.lenient().when(teacherProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(teacherProfile));

        AppUser user = AppUser.builder()
                .id(userId)
                .email("verified-teacher@test.com")
                .fullName("Verified Teacher")
                .phoneNumber("+84912345678")
                .phoneVerifiedAt(Instant.now())
                .build();
        org.mockito.Mockito.lenient().when(appUserRepository.findById(userId))
                .thenReturn(Optional.of(user));
        KycRequest verifiedKyc = new KycRequest();
        verifiedKyc.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        org.mockito.Mockito.lenient()
                .when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(
                        teacherProfileId))
                .thenReturn(Optional.of(verifiedKyc));

        wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .teacher(teacherProfile)
                .balance(new BigDecimal("2000000"))
                .frozenBalance(BigDecimal.ZERO)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 58 — createWithdrawalRequest (UC-27 Withdraw Teacher Revenue) — 9 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 58 - createWithdrawalRequest (UC-27)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CreateWithdrawal {

    /** Stubs everything a request needs to reach the happy path. */
    private void stubAcceptedRequest(UUID withdrawalId) {
        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(
                WalletOwnerType.TEACHER, teacherProfileId)).thenReturn(Optional.of(wallet));
        when(withdrawalRepository.countByTeacherIdAndStatus(
                teacherProfileId, WithdrawalStatus.PENDING)).thenReturn(0L);
        when(withdrawalRepository.countByTeacherIdAndCreatedAtAfter(
                eq(teacherProfileId), any(LocalDateTime.class))).thenReturn(0L);
        when(securityService.encryptAccountNumber("123456789"))
                .thenReturn("enc:v1:encrypted-account");
        when(withdrawalRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> {
                    WithdrawalRequest saved = invocation.getArgument(0);
                    saved.setId(withdrawalId);
                    assertNotNull(saved.getBankQrCode());
                    assertEquals("image/png", saved.getBankQrContentType());
                    return saved;
                });
        when(withdrawalMapper.toResponse(any(WithdrawalRequest.class)))
                .thenReturn(new WithdrawalRequestResponse());
    }

    @Test
    @Order(1)
    @DisplayName("UTCID01 (N) - new bank account -> PENDING, balance reserved, finance notified")
    void createWithdrawalRequest_SerializesAndSecuresFinancialRequest() {
        CreateWithdrawalRequest request = newRequest();
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequestResponse response = WithdrawalRequestResponse.builder()
                .id(withdrawalId.toString())
                .status(WithdrawalStatus.PENDING)
                .build();

        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(com.manabihub.wallet.enums.WalletOwnerType.TEACHER, teacherProfileId))
                .thenReturn(Optional.of(wallet));
        when(withdrawalRepository.countByTeacherIdAndStatus(
                teacherProfileId,
                WithdrawalStatus.PENDING
        )).thenReturn(0L);
        when(withdrawalRepository.countByTeacherIdAndCreatedAtAfter(
                eq(teacherProfileId),
                any(LocalDateTime.class)
        )).thenReturn(0L);
        when(securityService.encryptAccountNumber("123456789"))
                .thenReturn("enc:v1:encrypted-account");
        when(withdrawalRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> {
                    WithdrawalRequest saved = invocation.getArgument(0);
                    saved.setId(withdrawalId);
                    return saved;
                });
        when(withdrawalMapper.toResponse(any(WithdrawalRequest.class)))
                .thenReturn(response);

        WithdrawalRequestResponse result =
                withdrawalService.createWithdrawalRequest(userIdString, request);

        assertNotNull(result);
        assertEquals(WithdrawalStatus.PENDING, result.getStatus());
        verify(walletRepository).findByOwnerTypeAndTeacher_IdForUpdate(com.manabihub.wallet.enums.WalletOwnerType.TEACHER, teacherProfileId);
        verify(otpService).consumeVerification(userIdString, request);
        verify(walletService).reserveBalance(
                teacherProfileId.toString(),
                request.getAmount(),
                withdrawalId.toString()
        );
        verify(notificationService).notifyFinanceManager(
                withdrawalId,
                request.getAmount()
        );
    }

    @Test
    @Order(2)
    @DisplayName("UTCID02 (N) - saved account -> reuses the stored encrypted number")
    void createWithdrawalRequest_WithSavedAccount_UsesOwnedEncryptedAccount() {
        UUID accountId = UUID.randomUUID();
        TeacherBankAccount account = TeacherBankAccount.builder()
                .id(accountId)
                .teacherId(teacherProfileId)
                .bankCode("VCB")
                .bankName("Vietcombank")
                .accountNumber("enc:v1:saved")
                .accountHolderName("NGUYEN VAN A")
                .build();
        CreateWithdrawalRequest request = newRequest();
        request.setBankAccount(null);
        request.setBankAccountId(accountId.toString());
        request.setSaveAccount(false);
        UUID withdrawalId = UUID.randomUUID();

        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(com.manabihub.wallet.enums.WalletOwnerType.TEACHER, teacherProfileId))
                .thenReturn(Optional.of(wallet));
        when(withdrawalRepository.countByTeacherIdAndStatus(
                teacherProfileId,
                WithdrawalStatus.PENDING
        )).thenReturn(0L);
        when(withdrawalRepository.countByTeacherIdAndCreatedAtAfter(
                eq(teacherProfileId),
                any(LocalDateTime.class)
        )).thenReturn(0L);
        when(bankAccountRepository.findByIdAndTeacherId(accountId, teacherProfileId))
                .thenReturn(Optional.of(account));
        when(withdrawalRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> {
                    WithdrawalRequest saved = invocation.getArgument(0);
                    saved.setId(withdrawalId);
                    assertEquals(
                            "enc:v1:saved",
                            saved.getBankAccountSnapshot().getAccountNumber()
                    );
                    return saved;
                });
        when(withdrawalMapper.toResponse(any())).thenReturn(new WithdrawalRequestResponse());

        withdrawalService.createWithdrawalRequest(userIdString, request);

        verify(securityService, never()).decryptAccountNumber(any());
        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    @Order(3)
    @DisplayName("UTCID03 (B) - amount 500.000 = exactly the threshold -> accepted")
    void createWithdrawalRequest_AmountExactlyAtThreshold_IsAccepted() {
        CreateWithdrawalRequest request = newRequest();
        request.setAmount(minimumPayout);
        UUID withdrawalId = UUID.randomUUID();
        stubAcceptedRequest(withdrawalId);

        withdrawalService.createWithdrawalRequest(userIdString, request);

        verify(otpService).consumeVerification(userIdString, request);
        verify(walletService).reserveBalance(
                teacherProfileId.toString(), minimumPayout, withdrawalId.toString());
    }

    @Test
    @Order(4)
    @DisplayName("UTCID04 (B) - amount 499.999 = threshold - 1 -> PAYOUT_AMOUNT_BELOW_MINIMUM")
    void createWithdrawalRequest_AmountOneUnitBelowThreshold_IsRejected() {
        CreateWithdrawalRequest request = newRequest();
        request.setAmount(minimumPayout.subtract(BigDecimal.ONE));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.createWithdrawalRequest(userIdString, request)
        );

        assertEquals(MessageCodes.PAYOUT_AMOUNT_BELOW_MINIMUM, exception.getMessageCode());
        verifyNoInteractions(otpService, walletService);
        verify(withdrawalRepository, never()).saveAndFlush(any());
    }

    @Test
    @Order(5)
    @DisplayName("UTCID05 (A) - amount far below the threshold -> OTP is not consumed")
    void createWithdrawalRequest_AmountBelowMinimum_DoesNotConsumeOtp() {
        CreateWithdrawalRequest request = newRequest();
        request.setAmount(new BigDecimal("100000"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.createWithdrawalRequest(userIdString, request)
        );

        assertEquals(MessageCodes.PAYOUT_AMOUNT_BELOW_MINIMUM, exception.getMessageCode());
        verifyNoInteractions(otpService, walletService);
        verify(withdrawalRepository, never()).saveAndFlush(any());
    }

    @Test
    @Order(6)
    @DisplayName("UTCID06 (A) - runtime threshold 700.000 beats the request -> rejected early")
    void createWithdrawalRequest_UsesRuntimePayoutThresholdBeforeOtp() {
        CreateWithdrawalRequest request = newRequest();
        request.setAmount(new BigDecimal("600000"));
        when(commercialPolicyService.getCurrentPolicy())
                .thenReturn(policy(new BigDecimal("700000")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.createWithdrawalRequest(userIdString, request)
        );

        assertEquals(MessageCodes.PAYOUT_AMOUNT_BELOW_MINIMUM, exception.getMessageCode());
        verify(commercialPolicyService).getCurrentPolicy();
        verifyNoInteractions(otpService, walletService);
        verify(walletRepository, never()).findByOwnerTypeAndTeacher_IdForUpdate(eq(com.manabihub.wallet.enums.WalletOwnerType.TEACHER), any());
    }

    @Test
    @Order(7)
    @DisplayName("UTCID07 (A) - wallet not found -> WALLET_NOT_FOUND, OTP untouched")
    void createWithdrawalRequest_WalletNotFound_DoesNotConsumeOtp() {
        CreateWithdrawalRequest request = newRequest();
        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(com.manabihub.wallet.enums.WalletOwnerType.TEACHER, teacherProfileId))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.createWithdrawalRequest(userIdString, request)
        );

        assertEquals(MessageCodes.WALLET_NOT_FOUND, exception.getMessageCode());
        verifyNoInteractions(otpService, walletService);
    }

    @Test
    @Order(8)
    @DisplayName("UTCID08 (A) - a PENDING request already exists -> PAYOUT_PENDING_REQUEST_EXISTS")
    void createWithdrawalRequest_WhenAPendingRequestExists_IsRejected() {
        CreateWithdrawalRequest request = newRequest();
        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(
                WalletOwnerType.TEACHER, teacherProfileId)).thenReturn(Optional.of(wallet));
        when(withdrawalRepository.countByTeacherIdAndStatus(
                teacherProfileId, WithdrawalStatus.PENDING)).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.createWithdrawalRequest(userIdString, request)
        );

        assertEquals(MessageCodes.PAYOUT_PENDING_REQUEST_EXISTS, exception.getMessageCode());
        verifyNoInteractions(otpService, walletService);
        verify(withdrawalRepository, never()).saveAndFlush(any());
    }

    @Test
    @Order(9)
    @DisplayName("UTCID09 (A) - 2 requests already made this month -> PAYOUT_MONTHLY_LIMIT_EXCEEDED")
    void createWithdrawalRequest_WhenMonthlyLimitIsReached_IsRejected() {
        CreateWithdrawalRequest request = newRequest();
        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(
                WalletOwnerType.TEACHER, teacherProfileId)).thenReturn(Optional.of(wallet));
        when(withdrawalRepository.countByTeacherIdAndStatus(
                teacherProfileId, WithdrawalStatus.PENDING)).thenReturn(0L);
        when(withdrawalRepository.countByTeacherIdAndCreatedAtAfter(
                eq(teacherProfileId), any(LocalDateTime.class))).thenReturn(2L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.createWithdrawalRequest(userIdString, request)
        );

        assertEquals(MessageCodes.PAYOUT_MONTHLY_LIMIT_EXCEEDED, exception.getMessageCode());
        verifyNoInteractions(otpService, walletService);
        verify(withdrawalRepository, never()).saveAndFlush(any());
    }

    @Test
    @Order(10)
    @DisplayName("UTCID10 (A) - phone is not verified -> reject before wallet and OTP")
    void createWithdrawalRequest_RequiresVerifiedPhoneBeforeWalletAndOtp() {
        CreateWithdrawalRequest request = newRequest();
        AppUser unverified = AppUser.builder()
                .id(userId)
                .email("unverified-phone@test.com")
                .fullName("Unverified Phone")
                .build();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(unverified));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.createWithdrawalRequest(userIdString, request));

        assertEquals(MessageCodes.PAYOUT_PHONE_VERIFICATION_REQUIRED, exception.getMessageCode());
        verify(walletRepository, never()).findByOwnerTypeAndTeacher_IdForUpdate(any(), any());
        verifyNoInteractions(otpService, walletService);
        verify(withdrawalRepository, never()).saveAndFlush(any());
    }

    @Test
    @Order(11)
    @DisplayName("UTCID11 (A) - CCCD is not verified -> reject before wallet and OTP")
    void createWithdrawalRequest_RequiresVerifiedIdentityBeforeWalletAndOtp() {
        CreateWithdrawalRequest request = newRequest();
        KycRequest pendingKyc = new KycRequest();
        pendingKyc.setIdentityStatus(IdentityVerificationStatus.PROCESSING);
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(
                teacherProfileId)).thenReturn(Optional.of(pendingKyc));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.createWithdrawalRequest(userIdString, request));

        assertEquals(MessageCodes.MSG_KYC_002, exception.getMessageCode());
        verify(walletRepository, never()).findByOwnerTypeAndTeacher_IdForUpdate(any(), any());
        verifyNoInteractions(otpService, walletService);
        verify(withdrawalRepository, never()).saveAndFlush(any());
    }

    @Test
    @Order(12)
    @DisplayName("UTCID12 (N) - direct-sdk sandbox identity unlocks withdrawal without server timestamp")
    void createWithdrawalRequest_AcceptsDirectSdkSandboxIdentity() {
        ReflectionTestUtils.setField(withdrawalService, "identityVerificationMode", "direct-sdk");
        UUID withdrawalId = UUID.randomUUID();
        stubAcceptedRequest(withdrawalId);

        WithdrawalRequestResponse response = withdrawalService.createWithdrawalRequest(
                userIdString,
                newRequest()
        );

        assertNotNull(response);
        verify(withdrawalRepository).saveAndFlush(any(WithdrawalRequest.class));
    }

    @Test
    @Order(13)
    @DisplayName("UTCID13 (N) - student entry-point identity unlocks teacher withdrawal")
    void createWithdrawalRequest_AcceptsIdentityVerifiedThroughStudentEntryPoint() {
        KycRequest pendingKyc = new KycRequest();
        pendingKyc.setIdentityStatus(IdentityVerificationStatus.PROCESSING);
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(
                teacherProfileId)).thenReturn(Optional.of(pendingKyc));
        AccountIdentityVerification sharedVerification = new AccountIdentityVerification();
        sharedVerification.setUserId(userId);
        sharedVerification.setProvider("VNPT_EKYC_WEB_SDK");
        when(accountIdentityVerificationService.findVerified(userId))
                .thenReturn(Optional.of(sharedVerification));

        UUID withdrawalId = UUID.randomUUID();
        stubAcceptedRequest(withdrawalId);

        CreateWithdrawalRequest request = newRequest();
        WithdrawalRequestResponse response = withdrawalService.createWithdrawalRequest(
                userIdString,
                request);

        assertNotNull(response);
        verify(otpService).consumeVerification(userIdString, request);
        verify(withdrawalRepository).saveAndFlush(any(WithdrawalRequest.class));
    }

    @Test
    @Order(14)
    @DisplayName("UTCID14 (A) - teacher withdrawal rejects an invalid QR image")
    void createWithdrawalRequest_RejectsInvalidBankQr() {
        CreateWithdrawalRequest request = newRequest();
        request.setBankQrDataUrl("data:image/png;base64,AAAA");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.createWithdrawalRequest(userIdString, request));

        assertEquals(MessageCodes.PAYOUT_BANK_QR_INVALID, exception.getMessageCode());
        verifyNoInteractions(walletRepository, otpService, walletService);
        verify(withdrawalRepository, never()).saveAndFlush(any());
    }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 59 — cancelWithdrawal (UC-27 Withdraw Teacher Revenue) — 3 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 59 - cancelWithdrawal (UC-27)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CancelWithdrawal {

    @Test
    @Order(1)
    @DisplayName("UTCID01 (N) - PENDING -> CANCELLED, balance released, teacher notified")
    void cancelWithdrawal_LocksRequestAndNotifiesOnlyRequestingTeacher() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest request = WithdrawalRequest.builder()
                .id(withdrawalId)
                .teacherId(teacherProfileId)
                .requestedAmount(new BigDecimal("1000000"))
                .status(WithdrawalStatus.PENDING)
                .build();
        when(withdrawalRepository.findByIdAndTeacherIdWithLock(
                withdrawalId,
                teacherProfileId
        )).thenReturn(Optional.of(request));

        withdrawalService.cancelWithdrawal(userIdString, withdrawalId.toString());

        assertEquals(WithdrawalStatus.CANCELLED, request.getStatus());
        verify(walletService).releaseBalance(
                teacherProfileId.toString(),
                request.getRequestedAmount(),
                withdrawalId.toString()
        );
        verify(notificationService).notifyTeacherCancellation(
                userId,
                null,
                request.getRequestedAmount()
        );
    }

    @Test
    @Order(2)
    @DisplayName("UTCID02 (A) - request not found for this teacher -> PAYOUT_WITHDRAWAL_NOT_FOUND")
    void cancelWithdrawal_WhenRequestIsNotFound_IsRejected() {
        UUID withdrawalId = UUID.randomUUID();
        when(withdrawalRepository.findByIdAndTeacherIdWithLock(withdrawalId, teacherProfileId))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.cancelWithdrawal(userIdString, withdrawalId.toString())
        );

        assertEquals(MessageCodes.PAYOUT_WITHDRAWAL_NOT_FOUND, exception.getMessageCode());
        verifyNoInteractions(walletService);
        verify(withdrawalRepository, never()).save(any());
    }

    @Test
    @Order(3)
    @DisplayName("UTCID03 (A) - request already APPROVED -> PAYOUT_CANNOT_CANCEL")
    void cancelWithdrawal_WhenRequestIsNoLongerPending_IsRejected() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest request = WithdrawalRequest.builder()
                .id(withdrawalId)
                .teacherId(teacherProfileId)
                .requestedAmount(new BigDecimal("1000000"))
                .status(WithdrawalStatus.APPROVED)
                .build();
        when(withdrawalRepository.findByIdAndTeacherIdWithLock(withdrawalId, teacherProfileId))
                .thenReturn(Optional.of(request));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> withdrawalService.cancelWithdrawal(userIdString, withdrawalId.toString())
        );

        assertEquals("PAYOUT_CANNOT_CANCEL", exception.getMessageCode());
        assertEquals(WithdrawalStatus.APPROVED, request.getStatus());
        verifyNoInteractions(walletService);
        verify(withdrawalRepository, never()).save(any());
    }
    }

    private CreateWithdrawalRequest newRequest() {
        return CreateWithdrawalRequest.builder()
                .amount(new BigDecimal("1000000"))
                .otpCode("123456")
                .bankAccount(BankAccountDto.builder()
                        .bankCode("VCB")
                        .bankName("Vietcombank")
                        .accountHolderName("NGUYEN VAN A")
                        .accountNumber("123456789")
                        .build())
                .bankQrDataUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
                .build();
    }

    private CommercialPolicy policy(BigDecimal payoutThreshold) {
        return new CommercialPolicy(
                "VND",
                new BigDecimal("0.20"),
                7,
                30,
                14,
                payoutThreshold,
                BigDecimal.ZERO,
                1,
                2,
                "test-policy",
                Instant.parse("2026-07-28T00:00:00Z"));
    }
}
