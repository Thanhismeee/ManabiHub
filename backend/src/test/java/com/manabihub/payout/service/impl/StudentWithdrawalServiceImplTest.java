package com.manabihub.payout.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.AccountIdentityVerification;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.AccountIdentityVerificationService;
import com.manabihub.payout.dto.request.BankAccountDto;
import com.manabihub.payout.dto.request.CreateWithdrawalRequest;
import com.manabihub.payout.dto.response.WithdrawalRequestResponse;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.payout.mapper.WithdrawalMapper;
import com.manabihub.payout.repository.StudentBankAccountRepository;
import com.manabihub.payout.repository.WithdrawalRequestRepository;
import com.manabihub.payout.security.PayoutSecurityService;
import com.manabihub.payout.service.WithdrawalNotificationService;
import com.manabihub.payout.service.WithdrawalOtpService;
import com.manabihub.payout.service.StudentBankOwnershipVerificationService;
import com.manabihub.systemconfig.model.CommercialPolicy;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.service.StudentWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.manabihub.payout.entity.StudentBankAccount;
import com.manabihub.payout.dto.response.StudentBankAccountResponse;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentWithdrawalServiceImplTest {

    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private StudentBankAccountRepository bankAccountRepository;
    @Mock private StudentWalletService studentWalletService;
    @Mock private WithdrawalOtpService otpService;
    @Mock private WithdrawalMapper withdrawalMapper;
    @Mock private PayoutSecurityService securityService;
    @Mock private CommercialPolicyService commercialPolicyService;
    @Mock private WithdrawalNotificationService notificationService;
    @Mock private StudentBankOwnershipVerificationService ownershipVerificationService;
    @Mock private AccountIdentityVerificationService accountIdentityVerificationService;

    @InjectMocks private StudentWithdrawalServiceImpl service;

    private UUID userId;
    private StudentProfile student;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setPhoneNumber("+84912345678");
        user.setPhoneVerifiedAt(Instant.now());
        student = new StudentProfile();
        student.setId(UUID.randomUUID());
        student.setUser(user);
        student.setIdentityVerifiedAt(Instant.now());
        student.setIdentityFullName("NGUYEN VAN A");
        wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .ownerType(WalletOwnerType.STUDENT)
                .student(student)
                .balance(new BigDecimal("500000.00"))
                .withdrawableBalance(new BigDecimal("300000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .frozenWithdrawableBalance(BigDecimal.ZERO)
                .currency("VND")
                .build();
        org.mockito.Mockito.lenient()
                .when(commercialPolicyService.getCurrentPolicy())
                .thenReturn(policy());
        org.mockito.Mockito.lenient()
                .when(ownershipVerificationService.verify(true))
                .thenReturn(new StudentBankOwnershipVerificationService.VerificationEvidence(
                        true, "MOCK_LOCAL", LocalDateTime.now()));
    }

    @Test
    void createWithdrawal_reservesOnlyWithdrawableRefundBalance() {
        student.setIdentityProvider("VNPT_EKYC_WEB_SDK_UAT");
        ReflectionTestUtils.setField(service, "identityVerificationMode", "direct-sdk");
        CreateWithdrawalRequest request = request(new BigDecimal("200000.00"));
        UUID withdrawalId = UUID.randomUUID();
        stubOwnerAndWallet();
        when(withdrawalRequestRepository.countByStudentIdAndStatus(
                student.getId(), WithdrawalStatus.PENDING)).thenReturn(0L);
        when(withdrawalRequestRepository.countByStudentIdAndCreatedAtAfter(
                eq(student.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(securityService.encryptAccountNumber("0123456789"))
                .thenReturn("enc:student-account");
        when(withdrawalRequestRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    WithdrawalRequest saved = invocation.getArgument(0);
                    saved.setId(withdrawalId);
                    return saved;
                });
        when(withdrawalMapper.toResponse(any())).thenReturn(
                WithdrawalRequestResponse.builder()
                        .id(withdrawalId.toString())
                        .status(WithdrawalStatus.PENDING)
                        .build());

        WithdrawalRequestResponse response = service.createWithdrawal(userId, request);

        assertEquals(WithdrawalStatus.PENDING, response.getStatus());
        verify(otpService).consumeVerification(userId.toString(), request);
        verify(studentWalletService).reserveForWithdrawal(
                student.getId(), withdrawalId, request.getAmount());
        verify(notificationService).notifyFinanceManager(
                withdrawalId, request.getAmount(), "STUDENT");
    }

    @Test
    void createWithdrawal_rejectsTopUpOnlyBalanceBeforeOtp() {
        wallet.setWithdrawableBalance(BigDecimal.ZERO);
        stubOwnerAndWallet();

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.createWithdrawal(
                        userId, request(new BigDecimal("100000.00"))));

        assertEquals(MessageCodes.MSG_WALLET_004, error.getMessageCode());
        verify(otpService, never()).consumeVerification(any(), any());
        verify(withdrawalRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void createWithdrawal_requiresVerifiedPhoneBeforeWalletOrOtp() {
        student.getUser().setPhoneVerifiedAt(null);
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.createWithdrawal(
                        userId, request(new BigDecimal("100000.00"))));

        assertEquals(MessageCodes.PAYOUT_PHONE_VERIFICATION_REQUIRED, error.getMessageCode());
        verify(studentWalletService, never()).getOrCreateStudentWallet(any());
        verify(walletRepository, never()).findByOwnerTypeAndStudent_IdForUpdate(any(), any());
        verify(otpService, never()).consumeVerification(any(), any());
        verify(withdrawalRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void createWithdrawal_requiresVerifiedIdentityBeforeWalletOrOtp() {
        student.setIdentityVerifiedAt(null);
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.createWithdrawal(
                        userId, request(new BigDecimal("100000.00"))));

        assertEquals(MessageCodes.MSG_WALLET_005, error.getMessageCode());
        verify(studentWalletService, never()).getOrCreateStudentWallet(any());
        verify(walletRepository, never()).findByOwnerTypeAndStudent_IdForUpdate(any(), any());
        verify(otpService, never()).consumeVerification(any(), any());
        verify(withdrawalRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void createWithdrawal_acceptsIdentityVerifiedThroughTeacherEntryPoint() {
        student.setIdentityVerifiedAt(null);
        student.setIdentityFullName(null);
        ReflectionTestUtils.setField(service, "identityVerificationMode", "direct-sdk");
        AccountIdentityVerification sharedVerification = new AccountIdentityVerification();
        sharedVerification.setUserId(userId);
        sharedVerification.setProvider("VNPT_EKYC_WEB_SDK");
        sharedVerification.setFullName("NGUYEN VAN A");
        sharedVerification.setProvider("VNPT_EKYC_WEB_SDK");
        when(accountIdentityVerificationService.findVerified(userId))
                .thenReturn(Optional.of(sharedVerification));

        CreateWithdrawalRequest request = request(new BigDecimal("200000.00"));
        UUID withdrawalId = UUID.randomUUID();
        stubOwnerAndWallet();
        when(withdrawalRequestRepository.countByStudentIdAndStatus(
                student.getId(), WithdrawalStatus.PENDING)).thenReturn(0L);
        when(withdrawalRequestRepository.countByStudentIdAndCreatedAtAfter(
                eq(student.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(securityService.encryptAccountNumber("0123456789"))
                .thenReturn("enc:student-account");
        when(withdrawalRequestRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    WithdrawalRequest saved = invocation.getArgument(0);
                    saved.setId(withdrawalId);
                    return saved;
                });
        when(withdrawalMapper.toResponse(any())).thenReturn(
                WithdrawalRequestResponse.builder()
                        .id(withdrawalId.toString())
                        .status(WithdrawalStatus.PENDING)
                        .build());

        WithdrawalRequestResponse response = service.createWithdrawal(userId, request);

        assertEquals(WithdrawalStatus.PENDING, response.getStatus());
        verify(otpService).consumeVerification(userId.toString(), request);
        verify(studentWalletService).reserveForWithdrawal(
                student.getId(), withdrawalId, request.getAmount());
    }

    @Test
    void createWithdrawal_rejectsBankAccountHeldByAnotherPerson() {
        CreateWithdrawalRequest request = request(new BigDecimal("200000.00"));
        request.getBankAccount().setAccountHolderName("TRAN VAN KHAC");
        stubOwnerAndWallet();
        when(withdrawalRequestRepository.countByStudentIdAndStatus(
                student.getId(), WithdrawalStatus.PENDING)).thenReturn(0L);
        when(withdrawalRequestRepository.countByStudentIdAndCreatedAtAfter(
                eq(student.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(securityService.encryptAccountNumber("0123456789"))
                .thenReturn("enc:student-account");

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.createWithdrawal(userId, request));

        assertEquals(MessageCodes.MSG_WALLET_005, error.getMessageCode());
        verify(otpService, never()).consumeVerification(any(), any());
        verify(withdrawalRequestRepository, never()).saveAndFlush(any());
        verify(studentWalletService, never()).reserveForWithdrawal(any(), any(), any());
    }

    @Test
    void createWithdrawal_requiresSimulatedOwnershipVerificationBeforeOtp() {
        CreateWithdrawalRequest request = request(new BigDecimal("100000.00"));
        request.setOwnershipConfirmed(false);
        stubOwnerAndWallet();
        when(withdrawalRequestRepository.countByStudentIdAndStatus(
                student.getId(), WithdrawalStatus.PENDING)).thenReturn(0L);
        when(withdrawalRequestRepository.countByStudentIdAndCreatedAtAfter(
                eq(student.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(ownershipVerificationService.verify(false)).thenThrow(
                new BusinessException(
                        MessageCodes.PAYOUT_BANK_OWNERSHIP_REQUIRED,
                        "Ownership verification is required"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.createWithdrawal(userId, request));

        assertEquals(MessageCodes.PAYOUT_BANK_OWNERSHIP_REQUIRED, error.getMessageCode());
        verify(otpService, never()).consumeVerification(any(), any());
        verify(withdrawalRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void getSavedBankAccounts_returnsOnlyOwnershipVerifiedAccounts() {
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        AccountIdentityVerification verifiedIdentity = new AccountIdentityVerification();
        verifiedIdentity.setFullName("NGUYEN VAN A");
        when(accountIdentityVerificationService.findVerified(userId))
                .thenReturn(Optional.of(verifiedIdentity));
        StudentBankAccount verified = StudentBankAccount.builder()
                .id(UUID.randomUUID())
                .studentId(student.getId())
                .bankCode("VCB")
                .bankName("Vietcombank")
                .accountNumber("enc:student-account")
                .accountHolderName("NGUYEN VAN A")
                .ownershipVerified(true)
                .build();
        StudentBankAccount holderNameMismatch = StudentBankAccount.builder()
                .id(UUID.randomUUID())
                .studentId(student.getId())
                .bankCode("MB")
                .bankName("MB Bank")
                .accountNumber("enc:mismatched-account")
                .accountHolderName("TRAN VAN B")
                .ownershipVerified(true)
                .build();
        when(bankAccountRepository
                .findByStudentIdAndOwnershipVerifiedTrueOrderByCreatedAtDesc(student.getId()))
                .thenReturn(List.of(verified, holderNameMismatch));
        when(securityService.maskAccountNumber("enc:student-account")).thenReturn("****6789");

        List<StudentBankAccountResponse> accounts = service.getSavedBankAccounts(userId);

        assertEquals(1, accounts.size());
        assertEquals("VCB", accounts.get(0).getBankCode());
        assertEquals("****6789", accounts.get(0).getAccountNumber());
        assertTrue(accounts.get(0).isOwnershipVerified());
    }

    @Test
    void cancelWithdrawal_releasesReservedWithdrawableBalance() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId)
                .ownerType(WalletOwnerType.STUDENT)
                .studentId(student.getId())
                .walletId(wallet.getId())
                .requestedAmount(new BigDecimal("100000.00"))
                .status(WithdrawalStatus.PENDING)
                .build();
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(withdrawalRequestRepository.findByIdAndStudentIdWithLock(
                withdrawalId, student.getId())).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.save(withdrawal)).thenReturn(withdrawal);
        when(withdrawalMapper.toResponse(withdrawal)).thenReturn(
                WithdrawalRequestResponse.builder()
                        .id(withdrawalId.toString())
                        .status(WithdrawalStatus.CANCELLED)
                        .build());

        WithdrawalRequestResponse response = service.cancelWithdrawal(userId, withdrawalId);

        assertEquals(WithdrawalStatus.CANCELLED, response.getStatus());
        verify(studentWalletService).releaseWithdrawal(
                student.getId(),
                withdrawalId,
                withdrawal.getRequestedAmount(),
                WalletTransactionType.WITHDRAWAL_CANCELLED,
                "Student cancelled the withdrawal request");
    }

    private void stubOwnerAndWallet() {
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(studentWalletService.getOrCreateStudentWallet(student.getId())).thenReturn(wallet);
        when(walletRepository.findByOwnerTypeAndStudent_IdForUpdate(WalletOwnerType.STUDENT, student.getId()))
                .thenReturn(Optional.of(wallet));
    }

    private CreateWithdrawalRequest request(BigDecimal amount) {
        return CreateWithdrawalRequest.builder()
                .amount(amount)
                .otpCode("123456")
                .ownershipConfirmed(true)
                .bankAccount(BankAccountDto.builder()
                        .bankCode("VCB")
                        .bankName("Vietcombank")
                        .accountNumber("0123456789")
                        .accountHolderName("NGUYEN VAN A")
                        .build())
                .build();
    }

    private CommercialPolicy policy() {
        return new CommercialPolicy(
                "VND",
                new BigDecimal("0.20"),
                14,
                20,
                30,
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                1,
                2,
                "test-policy",
                Instant.parse("2026-08-01T00:00:00Z"));
    }
}
