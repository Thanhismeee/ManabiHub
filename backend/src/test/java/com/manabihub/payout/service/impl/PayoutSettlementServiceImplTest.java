package com.manabihub.payout.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.payout.dto.request.ManualTransferRequest;
import com.manabihub.payout.dto.request.PayoutQueueFilterRequest;
import com.manabihub.payout.dto.request.RejectPayoutRequest;
import com.manabihub.payout.entity.BankAccountSnapshot;
import com.manabihub.payout.entity.PayoutSettlement;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.enums.PayoutStatus;
import com.manabihub.payout.enums.PayoutTransferMethod;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.payout.repository.PayoutSettlementRepository;
import com.manabihub.payout.repository.PayoutReconciliationLogRepository;
import com.manabihub.payout.repository.WithdrawalRequestRepository;
import com.manabihub.payout.security.PayoutSecurityService;
import com.manabihub.payout.service.PayoutGateway;
import com.manabihub.payout.service.PayoutProofStorageService;
import com.manabihub.payout.service.PayoutReconciliationService;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import com.manabihub.wallet.service.StudentWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayoutSettlementServiceImplTest {

    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private PayoutSettlementRepository payoutSettlementRepository;
    @Mock private PayoutReconciliationLogRepository reconciliationLogRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InternalAdminAccountRepository internalAdminAccountRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private PayoutReconciliationService reconciliationService;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;
    @Mock private PayoutGateway payoutGateway;
    @Mock private PayoutProofStorageService proofStorageService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private PayoutSecurityService payoutSecurityService;
    @Mock private StudentWalletService studentWalletService;

    private PayoutSettlementServiceImpl service;
    private UUID adminId;
    private UUID requestId;
    private WithdrawalRequest request;
    private Wallet wallet;
    private TeacherProfile teacher;
    private InternalAdminAccount admin;
    private AtomicReference<PayoutSettlement> settlementRef;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        Role role = new Role();
        role.setCode(RoleCode.FINANCE_MANAGER);
        admin = new InternalAdminAccount();
        admin.setId(adminId);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin.setRole(role);

        AppUser user = new AppUser();
        user.setId(userId);
        user.setFullName("Nguyen Sensei");
        user.setEmail("teacher@example.com");
        user.setUserStatus(UserStatus.ACTIVE);
        teacher = new TeacherProfile();
        teacher.setId(teacherId);
        teacher.setDisplayName("Nguyen Sensei");
        teacher.setUser(user);

        BankAccountSnapshot bank = new BankAccountSnapshot();
        bank.setBankName("Vietcombank");
        bank.setBranch("Ha Noi");
        bank.setAccountHolderName("NGUYEN SENSEI");
        bank.setAccountNumber("0123456789");
        request = WithdrawalRequest.builder()
                .id(requestId)
                .teacherId(teacherId)
                .walletId(walletId)
                .requestedAmount(new BigDecimal("1000000.00"))
                .status(WithdrawalStatus.PENDING)
                .bankAccountSnapshot(bank)
                .requestedAt(LocalDateTime.now())
                .build();
        wallet = Wallet.builder()
                .id(walletId)
                .teacher(teacher)
                .balance(new BigDecimal("2000000.00"))
                .frozenBalance(new BigDecimal("1000000.00"))
                .currency("VND")
                .build();
        WalletTransaction reservation = WalletTransaction.builder()
                .walletId(walletId)
                .transactionType(WalletTransactionType.WITHDRAWAL_RESERVATION)
                .amount(new BigDecimal("1000000.00"))
                .direction(WalletDirection.OUT)
                .referenceType("WITHDRAWAL_REQUEST")
                .referenceId(requestId)
                .build();

        settlementRef = new AtomicReference<>();
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        when(internalAdminAccountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(withdrawalRequestRepository.findByIdWithLock(requestId)).thenReturn(Optional.of(request));
        when(withdrawalRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(walletRepository.findByOwnerTypeAndTeacher_Id(
                WalletOwnerType.TEACHER, teacherId)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(com.manabihub.wallet.enums.WalletOwnerType.TEACHER, teacherId)).thenReturn(Optional.of(wallet));
        when(payoutSettlementRepository.findByWithdrawalRequestId(requestId))
                .thenAnswer(ignored -> Optional.ofNullable(settlementRef.get()));
        when(payoutSettlementRepository.findByWithdrawalRequestIdWithLock(requestId))
                .thenAnswer(ignored -> Optional.ofNullable(settlementRef.get()));
        when(payoutSettlementRepository.findByIdWithLock(any()))
                .thenAnswer(ignored -> Optional.ofNullable(settlementRef.get()));
        when(payoutSettlementRepository.save(any(PayoutSettlement.class))).thenAnswer(invocation -> {
            PayoutSettlement settlement = invocation.getArgument(0);
            if (settlement.getId() == null) {
                settlement.setId(UUID.randomUUID());
            }
            settlementRef.set(settlement);
            return settlement;
        });
        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "WITHDRAWAL_REQUEST",
                requestId,
                WalletTransactionType.WITHDRAWAL_RESERVATION
        )).thenReturn(Optional.of(reservation));
        when(reconciliationService.reconcile(request, wallet, teacher))
                .thenReturn(matchedReconciliation());
        when(reconciliationService.reconcileCompleted(request, wallet, null))
                .thenReturn(matchedReconciliation());
        when(reconciliationService.reconcileCompleted(eq(request), eq(wallet), any(PayoutSettlement.class)))
                .thenReturn(matchedReconciliation());
        when(reconciliationService.reconcileRejected(eq(request), eq(wallet), any(PayoutSettlement.class)))
                .thenReturn(matchedReconciliation());
        when(reconciliationLogRepository.findByWithdrawalRequestIdOrderByCreatedAtDesc(
                eq(requestId), any())).thenReturn(List.of());
        when(payoutGateway.providerName()).thenReturn("TEST_GATEWAY");
        when(payoutSecurityService.decryptAccountNumber(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        when(payoutSecurityService.maskAccountNumber(any())).thenAnswer(
                invocation -> "****" + invocation.<String>getArgument(0).substring(6)
        );

        service = new PayoutSettlementServiceImpl(
                withdrawalRequestRepository,
                payoutSettlementRepository,
                reconciliationLogRepository,
                walletRepository,
                walletTransactionRepository,
                teacherProfileRepository,
                studentProfileRepository,
                internalAdminAccountRepository,
                currentUserService,
                reconciliationService,
                auditLogService,
                notificationService,
                payoutGateway,
                proofStorageService,
                transactionTemplate,
                payoutSecurityService,
                studentWalletService
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 60 — approvePayout (UC-33 Execute Payout Settlement) — 6 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 60 - approvePayout (UC-33)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ApprovePayout {

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("UTCID01 (N) - teacher payout -> wallet completed exactly once")
    void approvePayoutCompletesWalletExactlyOnce() {
        when(payoutGateway.transfer(any())).thenReturn(PayoutGateway.PayoutGatewayResult.builder()
                .success(true)
                .providerReference("BANK-123")
                .build());

        var first = service.approvePayout(requestId);
        var repeated = service.approvePayout(requestId);

        assertEquals(WithdrawalStatus.EXECUTED, first.getWithdrawalStatus());
        assertEquals(PayoutStatus.SUCCEEDED, repeated.getSettlementStatus());
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("1000000.00")));
        assertEquals(0, wallet.getFrozenBalance().compareTo(BigDecimal.ZERO));

        var detail = service.getPayoutDetail(requestId);
        var pageable = PageRequest.of(0, 20);
        var safePageable = PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("requestedAt"),
                Sort.Order.desc("id")
        ));
        when(withdrawalRequestRepository.findAll(
                any(Specification.class),
                eq(safePageable)
        )).thenReturn(new PageImpl<>(List.of(request), safePageable, 1));
        var queue = service.getPayoutQueue(new PayoutQueueFilterRequest(), pageable);
        var reviewed = service.reviewReconciliation(requestId);

        assertEquals(ReconciliationStatus.MATCHED, detail.getReconciliationStatus());
        assertTrue(detail.getReconciliationAlerts().isEmpty());
        assertEquals(ReconciliationStatus.MATCHED,
                queue.getContent().get(0).getReconciliationStatus());
        assertEquals(ReconciliationStatus.MATCHED, reviewed.getReconciliationStatus());
        verify(reconciliationService, times(3))
                .reconcileCompleted(eq(request), eq(wallet), any(PayoutSettlement.class));
        verify(payoutGateway, times(1)).transfer(any());
        verify(walletTransactionRepository, times(1)).save(any(WalletTransaction.class));
        verify(notificationService, times(1))
                .createNotification(any(), any(), any(), any(), eq("PAYOUT_SUCCESS"), any());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("UTCID02 (N) - student payout -> captures the reserved refund balance")
    void approveStudentPayoutCapturesReservedRefundBalance() {
        UUID studentId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        com.manabihub.identity.entity.AppUser studentUser =
                com.manabihub.identity.entity.AppUser.builder()
                        .id(studentUserId)
                        .email("student@example.com")
                        .fullName("Nguyen Student")
                        .userStatus(AccountStatus.ACTIVE)
                        .build();
        com.manabihub.identity.entity.StudentProfile student =
                com.manabihub.identity.entity.StudentProfile.builder()
                        .id(studentId)
                        .displayName("Nguyen Student")
                        .user(studentUser)
                        .build();
        request.setOwnerType(WalletOwnerType.STUDENT);
        request.setTeacherId(null);
        request.setStudentId(studentId);
        request.setWalletId(wallet.getId());
        wallet.setOwnerType(WalletOwnerType.STUDENT);
        wallet.setTeacher(null);
        wallet.setStudent(student);
        wallet.setWithdrawableBalance(request.getRequestedAmount());
        wallet.setFrozenWithdrawableBalance(request.getRequestedAmount());

        when(studentProfileRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(walletRepository.findByOwnerTypeAndStudent_IdForUpdate(com.manabihub.wallet.enums.WalletOwnerType.STUDENT, studentId)).thenReturn(Optional.of(wallet));
        when(reconciliationService.reconcileStudent(request, wallet, student))
                .thenReturn(matchedReconciliation());
        when(payoutGateway.transfer(any())).thenReturn(PayoutGateway.PayoutGatewayResult.builder()
                .success(true)
                .providerReference("BANK-STUDENT-123")
                .build());

        var response = service.approvePayout(requestId);

        assertEquals(WithdrawalStatus.EXECUTED, response.getWithdrawalStatus());
        verify(studentWalletService).completeWithdrawal(
                studentId,
                requestId,
                request.getRequestedAmount());
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
        verify(notificationService).createNotification(
                eq(studentUserId), any(), any(), any(), eq("PAYOUT_SUCCESS"), eq("/student/wallet"));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("UTCID03 (A) - critical reconciliation mismatch -> blocked before the gateway")
    void approvePayoutBlocksCriticalReconciliationBeforeGateway() {
        when(reconciliationService.reconcile(request, wallet, teacher))
                .thenReturn(criticalReconciliation());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.approvePayout(requestId)
        );

        assertEquals("MSG-ADM-005", error.getMessageCode());
        assertEquals(PayoutStatus.FAILED, settlementRef.get().getStatus());
        verify(payoutGateway, never()).transfer(any());
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("2000000.00")));
        verify(notificationService).createNotificationForAdminRole(
                eq("FINANCE_MANAGER"),
                any(),
                any(),
                eq("PAYOUT_ALERT"),
                eq("/admin/payouts/" + requestId)
        );
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("UTCID04 (A) - gateway needs a retry -> reserved money is kept")
    void approvePayoutKeepsReservedMoneyWhenGatewayNeedsRetry() {
        when(payoutGateway.transfer(any())).thenReturn(PayoutGateway.PayoutGatewayResult.builder()
                .success(false)
                .errorCode("TIMEOUT")
                .errorMessage("Provider timeout")
                .isRetryable(true)
                .build());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.approvePayout(requestId)
        );

        assertEquals("PAYOUT_PENDING_RETRY", error.getMessageCode());
        assertEquals(WithdrawalStatus.FAILED, request.getStatus());
        assertEquals(PayoutStatus.PENDING_RETRY, settlementRef.get().getStatus());
        assertEquals(0, wallet.getFrozenBalance().compareTo(new BigDecimal("1000000.00")));
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("UTCID05 (A) - concurrent double click -> PAYOUT_SETTLEMENT_PROCESSING")
    void approvePayoutRejectsConcurrentDoubleClick() {
        PayoutSettlement processing = baseSettlement();
        processing.setStatus(PayoutStatus.PROCESSING);
        processing.setProcessingStartedAt(Instant.now());
        settlementRef.set(processing);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.approvePayout(requestId)
        );

        assertEquals("PAYOUT_SETTLEMENT_PROCESSING", error.getMessageCode());
        verify(payoutGateway, never()).transfer(any());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("UTCID06 (A) - actor is not an active Finance Manager -> PAYOUT_PERMISSION_DENIED")
    void payoutRequiresActiveFinanceManagerFromDatabase() {
        admin.getRole().setCode(RoleCode.SYSTEM_ADMIN);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.approvePayout(requestId)
        );

        assertEquals("PAYOUT_PERMISSION_DENIED", error.getMessageCode());
        verify(withdrawalRequestRepository, never()).findByIdWithLock(any());
    }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 61 — rejectPayout (UC-33 Execute Payout Settlement) — 1 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 61 - rejectPayout (UC-33)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RejectPayout {

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("UTCID01 (N) - releases the reservation, repeated call is idempotent")
    void rejectPayoutReleasesReservationAndRepeatedCallIsIdempotent() {
        RejectPayoutRequest payload = new RejectPayoutRequest();
        payload.setReason("Bank account data does not match");

        service.rejectPayout(requestId, payload);
        service.rejectPayout(requestId, payload);

        assertEquals(WithdrawalStatus.REJECTED, request.getStatus());
        assertEquals(PayoutStatus.REJECTED, settlementRef.get().getStatus());
        assertEquals(0, wallet.getFrozenBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("2000000.00")));
        var detail = service.getPayoutDetail(requestId);
        assertEquals(ReconciliationStatus.MATCHED, detail.getReconciliationStatus());
        assertTrue(detail.getReconciliationAlerts().isEmpty());
        verify(reconciliationService, times(2))
                .reconcileRejected(eq(request), eq(wallet), any(PayoutSettlement.class));
        verify(walletTransactionRepository, times(1)).save(any(WalletTransaction.class));
        verify(notificationService, times(1))
                .createNotification(any(), any(), any(), any(), eq("PAYOUT_REJECTED"), any());
    }

    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 62 — confirmManualTransfer (UC-33 Execute Payout Settlement) — 2 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 62 - confirmManualTransfer (UC-33)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConfirmManualTransfer {

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("UTCID01 (N) - completes the wallet and keeps the proof metadata private")
    void manualTransferCompletesWalletAndKeepsPrivateProofMetadata() {
        ManualTransferRequest payload = new ManualTransferRequest();
        payload.setTransactionReference("VCB-20260726-001");
        payload.setTransferredAmount(new BigDecimal("1000000.00"));
        payload.setTransferredAt(Instant.now().minusSeconds(60));
        payload.setNote("Verified against the bank statement");
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "transfer-proof.pdf",
                "application/pdf",
                "%PDF-1.7 test".getBytes()
        );
        when(proofStorageService.store(requestId, proof))
                .thenReturn(new PayoutProofStorageService.StoredProof(
                        requestId + "/proof.pdf",
                        "transfer-proof.pdf",
                        "application/pdf",
                        proof.getSize()
                ));
        when(payoutSettlementRepository.existsByProviderAndProviderReferenceId(
                "MANUAL_BANK_TRANSFER",
                "VCB-20260726-001"
        )).thenReturn(false);

        var response = service.confirmManualTransfer(requestId, payload, proof);

        assertEquals(WithdrawalStatus.EXECUTED, response.getWithdrawalStatus());
        assertEquals(PayoutStatus.SUCCEEDED, response.getSettlementStatus());
        assertEquals(PayoutTransferMethod.MANUAL, response.getTransferMethod());
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("1000000.00")));
        assertEquals(0, wallet.getFrozenBalance().compareTo(BigDecimal.ZERO));
        assertEquals("VCB-20260726-001", settlementRef.get().getProviderReferenceId());
        assertEquals(requestId + "/proof.pdf", settlementRef.get().getManualProofStorageKey());
        verify(payoutGateway, never()).transfer(any());
        verify(walletTransactionRepository, times(1)).save(any(WalletTransaction.class));
        verify(reconciliationLogRepository, times(1)).save(any());
        verify(notificationService, times(1))
                .createNotification(any(), any(), any(), any(), eq("PAYOUT_SUCCESS"), any());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("UTCID02 (A) - amount mismatch -> rejected, orphan proof removed")
    void manualTransferRejectsAmountMismatchAndRemovesOrphanProof() {
        ManualTransferRequest payload = new ManualTransferRequest();
        payload.setTransactionReference("VCB-20260726-002");
        payload.setTransferredAmount(new BigDecimal("999999.00"));
        payload.setTransferredAt(Instant.now().minusSeconds(60));
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "transfer-proof.pdf",
                "application/pdf",
                "%PDF-1.7 test".getBytes()
        );
        String storageKey = requestId + "/orphan.pdf";
        when(proofStorageService.store(requestId, proof))
                .thenReturn(new PayoutProofStorageService.StoredProof(
                        storageKey,
                        "transfer-proof.pdf",
                        "application/pdf",
                        proof.getSize()
                ));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.confirmManualTransfer(requestId, payload, proof)
        );

        assertEquals("PAYOUT_MANUAL_AMOUNT_MISMATCH", error.getMessageCode());
        verify(proofStorageService).deleteQuietly(storageKey);
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("2000000.00")));
        assertEquals(0, wallet.getFrozenBalance().compareTo(new BigDecimal("1000000.00")));
    }

    }

    private PayoutSettlement baseSettlement() {
        return PayoutSettlement.builder()
                .id(UUID.randomUUID())
                .withdrawalRequestId(requestId)
                .teacherId(request.getTeacherId())
                .walletId(wallet.getId())
                .amount(request.getRequestedAmount())
                .currency("VND")
                .status(PayoutStatus.PROCESSING)
                .idempotencyKey("payout-" + requestId)
                .reconciliationStatus(ReconciliationStatus.MATCHED)
                .retryCount(0)
                .build();
    }

    private PayoutReconciliationService.ReconciliationResult matchedReconciliation() {
        return new PayoutReconciliationService.ReconciliationResult(
                ReconciliationStatus.MATCHED,
                List.of(),
                BigDecimal.ZERO,
                "CLEARED",
                false
        );
    }

    private PayoutReconciliationService.ReconciliationResult criticalReconciliation() {
        return new PayoutReconciliationService.ReconciliationResult(
                ReconciliationStatus.CRITICAL_MISMATCH,
                List.of(new PayoutReconciliationService.ReconciliationAlert(
                        "PAYOUT_RESERVATION_LEDGER_MISSING",
                        "CRITICAL",
                        "Reservation ledger is missing"
                )),
                BigDecimal.ZERO,
                "CLEARED",
                false
        );
    }
}
