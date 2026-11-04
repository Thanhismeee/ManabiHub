package com.manabihub.payout.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.payout.dto.request.ManualTransferRequest;
import com.manabihub.payout.dto.request.RejectPayoutRequest;
import com.manabihub.payout.dto.request.PayoutQueueFilterRequest;
import com.manabihub.payout.dto.response.PayoutDecisionResponse;
import com.manabihub.payout.dto.response.PayoutDetailResponse;
import com.manabihub.payout.dto.response.PayoutQueueItemResponse;
import com.manabihub.payout.dto.response.ReconciliationAlertResponse;
import com.manabihub.payout.dto.response.ReconciliationHistoryResponse;
import com.manabihub.payout.entity.BankAccountSnapshot;
import com.manabihub.payout.entity.PayoutReconciliationLog;
import com.manabihub.payout.entity.PayoutSettlement;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.enums.PayoutStatus;
import com.manabihub.payout.enums.PayoutNotificationStatus;
import com.manabihub.payout.enums.PayoutTransferMethod;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.payout.repository.PayoutReconciliationLogRepository;
import com.manabihub.payout.repository.PayoutQueueSpecification;
import com.manabihub.payout.repository.PayoutSettlementRepository;
import com.manabihub.payout.repository.WithdrawalRequestRepository;
import com.manabihub.payout.security.PayoutSecurityService;
import com.manabihub.payout.service.PayoutGateway;
import com.manabihub.payout.service.PayoutProofStorageService;
import com.manabihub.payout.service.PayoutReconciliationService;
import com.manabihub.payout.service.PayoutSettlementService;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import com.manabihub.wallet.service.StudentWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutSettlementServiceImpl implements PayoutSettlementService {

    private static final String FINANCE_ROLE = "FINANCE_MANAGER";
    private static final String TEACHER_WALLET_LINK = "/teacher/wallet";
    private static final String STUDENT_WALLET_LINK = "/student/wallet";
    private static final String WITHDRAWAL_REFERENCE = "WITHDRAWAL_REQUEST";
    private static final Duration PROCESSING_STALE_AFTER = Duration.ofMinutes(5);
    private static final Set<String> PAYOUT_QUEUE_SORTS = Set.of(
            "requestedAt", "updatedAt", "requestedAmount", "status", "id"
    );

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final PayoutSettlementRepository payoutSettlementRepository;
    private final PayoutReconciliationLogRepository reconciliationLogRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InternalAdminAccountRepository internalAdminAccountRepository;
    private final CurrentUserService currentUserService;
    private final PayoutReconciliationService reconciliationService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final PayoutGateway payoutGateway;
    private final PayoutProofStorageService proofStorageService;
    private final TransactionTemplate transactionTemplate;
    private final PayoutSecurityService payoutSecurityService;
    private final StudentWalletService studentWalletService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public Page<PayoutQueueItemResponse> getPayoutQueue(
            PayoutQueueFilterRequest filter,
            Pageable pageable
    ) {
        requireFinanceAdmin();
        PayoutQueueFilterRequest safeFilter = filter == null ? new PayoutQueueFilterRequest() : filter;
        validateQueueFilter(safeFilter);
        return withdrawalRequestRepository.findAll(
                PayoutQueueSpecification.from(safeFilter),
                safeQueuePageable(pageable)
        ).map(this::toQueueItem);
    }

    private Pageable safeQueuePageable(Pageable requested) {
        int page = Math.max(0, requested == null ? 0 : requested.getPageNumber());
        int size = Math.min(100, Math.max(1, requested == null ? 20 : requested.getPageSize()));
        List<Sort.Order> orders = requested == null ? List.of() : requested.getSort().stream()
                .filter(order -> PAYOUT_QUEUE_SORTS.contains(order.getProperty()))
                .toList();
        Sort sort = orders.isEmpty()
                ? Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id"))
                : Sort.by(orders).and(Sort.by(Sort.Order.desc("id")));
        return PageRequest.of(page, size, sort);
    }

    private void validateQueueFilter(PayoutQueueFilterRequest filter) {
        if ((filter.getMinAmount() != null && filter.getMinAmount().signum() < 0)
                || (filter.getMaxAmount() != null && filter.getMaxAmount().signum() < 0)) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Payout amount filters cannot be negative"
            );
        }
        if (filter.getMinAmount() != null && filter.getMaxAmount() != null
                && filter.getMinAmount().compareTo(filter.getMaxAmount()) > 0) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Minimum payout amount cannot exceed maximum payout amount"
            );
        }
        if (filter.getRequestedFrom() != null && filter.getRequestedTo() != null
                && !filter.getRequestedFrom().isBefore(filter.getRequestedTo())) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Payout range must satisfy from < to"
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public PayoutDetailResponse getPayoutDetail(UUID withdrawalRequestId) {
        requireFinanceAdmin();
        WithdrawalRequest request = findRequest(withdrawalRequestId);
        OwnerContext owner = findOwner(request, false);
        Wallet wallet = owner.wallet();
        PayoutSettlement settlement = payoutSettlementRepository
                .findByWithdrawalRequestId(withdrawalRequestId)
                .orElse(null);
        PayoutReconciliationService.ReconciliationResult reconciliation =
                reconcileForCurrentPhase(request, owner, settlement);

        return toDetail(request, owner, settlement, reconciliation);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public PayoutDetailResponse reviewReconciliation(UUID withdrawalRequestId) {
        InternalAdminAccount admin = requireFinanceAdmin();
        WithdrawalRequest request = findRequest(withdrawalRequestId);
        OwnerContext owner = findOwner(request, false);
        Wallet wallet = owner.wallet();
        PayoutSettlement settlement = payoutSettlementRepository
                .findByWithdrawalRequestId(withdrawalRequestId)
                .orElse(null);
        PayoutReconciliationService.ReconciliationResult reconciliation =
                reconcileForCurrentPhase(request, owner, settlement);
        if (settlement != null) {
            settlement.setReconciliationStatus(reconciliation.status());
            settlement.setReconciliationNote(reconciliationNote(reconciliation));
            payoutSettlementRepository.save(settlement);
        }
        saveReconciliationLog(
                request,
                settlement,
                wallet,
                reconciliation,
                admin.getId(),
                "DETAIL_REVIEW"
        );
        auditLogService.logAdminAction(
                admin.getId(),
                FINANCE_ROLE,
                "PAYOUT_RECONCILIATION_REVIEWED",
                "WITHDRAWAL_REQUEST",
                request.getId(),
                Map.of(),
                Map.of("reconciliationStatus", reconciliation.status().name()),
                Map.of("alertCount", reconciliation.alerts().size())
        );
        return toDetail(request, owner, settlement, reconciliation);
    }

    @Override
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public PayoutDecisionResponse approvePayout(UUID withdrawalRequestId) {
        InternalAdminAccount admin = requireFinanceAdmin();
        PreparedPayout prepared = transactionTemplate.execute(
                status -> preparePayout(withdrawalRequestId, admin)
        );
        if (prepared == null) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Payout preparation did not return a result",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        if (prepared.mode() == PrepareMode.BLOCKED) {
            notifyFinanceAlert(
                    prepared.request(),
                    "Phát hiện sai lệch quyết toán nghiêm trọng",
                    "Yêu cầu không được gửi tới cổng thanh toán vì dữ liệu đối soát không khớp."
            );
            throw new BusinessException(
                    prepared.blockMessageCode(),
                    prepared.blockMessage(),
                    HttpStatus.CONFLICT
            );
        }
        if (prepared.mode() == PrepareMode.ALREADY_COMPLETED) {
            return toDecision(prepared.request(), prepared.settlement());
        }

        PayoutGateway.PayoutGatewayResult gatewayResult = invokeGateway(prepared);
        PayoutGateway.PayoutGatewayResult recordedResult = transactionTemplate.execute(
                status -> recordGatewayResult(prepared.settlement().getId(), gatewayResult, admin)
        );
        if (recordedResult == null) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Payout gateway result could not be recorded",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        if (!recordedResult.isSuccess()) {
            notifyGatewayFailure(prepared.request(), recordedResult);
            notifyFinanceAlert(
                    prepared.request(),
                    recordedResult.isRetryable()
                            ? "Quyết toán đang chờ thử lại"
                            : "Cổng thanh toán từ chối quyết toán",
                    recordedResult.isRetryable()
                            ? "Hãy kiểm tra lại trạng thái nhà cung cấp trước khi thử lại yêu cầu."
                            : "Số dư đang giữ chưa bị khấu trừ; hãy kiểm tra cấu hình hoặc phản hồi nhà cung cấp."
            );
            String messageCode = recordedResult.isRetryable()
                    ? MessageCodes.PAYOUT_PENDING_RETRY
                    : MessageCodes.PAYOUT_GATEWAY_FAILED;
            throw new BusinessException(
                    messageCode,
                    recordedResult.isRetryable()
                            ? "The payout is pending retry."
                            : "The payout gateway rejected the transfer.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        FinalizeResult finalized;
        try {
            finalized = transactionTemplate.execute(
                    status -> finalizeSuccessfulPayout(prepared.settlement().getId(), admin)
            );
        } catch (RuntimeException exception) {
            notifyFinanceAlert(
                    prepared.request(),
                    "Không thể hoàn tất bút toán thanh toán",
                    "Nhà cung cấp có thể đã nhận lệnh chuyển tiền nhưng bút toán nội bộ thất bại. "
                            + "Không tạo giao dịch mới; hãy dùng chức năng thử lại/đối soát."
            );
            throw exception;
        }
        if (finalized == null) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Payout finalization did not return a result",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        if (finalized.blocked()) {
            notifyFinanceAlert(
                    finalized.request(),
                    "Cần hoàn tất đối soát sau chuyển tiền",
                    "Nhà cung cấp đã nhận giao dịch nhưng bút toán nội bộ đang bị chặn. Không tạo giao dịch chuyển tiền mới."
            );
            throw new BusinessException(
                    MessageCodes.MSG_ADM_005,
                    "Payout transfer succeeded but local finalization is blocked by reconciliation.",
                    HttpStatus.CONFLICT
            );
        }

        notifyPayoutSucceeded(finalized.request(), finalized.settlement());
        return toDecision(finalized.request(), finalized.settlement());
    }

    @Override
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public PayoutDecisionResponse retryPayout(UUID withdrawalRequestId) {
        requireFinanceAdmin();
        PayoutSettlement settlement = payoutSettlementRepository
                .findByWithdrawalRequestId(withdrawalRequestId)
                .orElseThrow(() -> settlementNotFound(withdrawalRequestId));
        boolean retryable = settlement.getStatus() == PayoutStatus.PENDING_RETRY
                || settlement.getStatus() == PayoutStatus.FAILED
                || (settlement.getStatus() == PayoutStatus.PROCESSING
                    && isStale(settlement.getProcessingStartedAt()));
        if (!retryable || settlement.getTransferMethod() == PayoutTransferMethod.MANUAL) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_RETRY_NOT_ALLOWED,
                    "This payout is not eligible for a gateway retry.",
                    HttpStatus.CONFLICT
            );
        }
        return approvePayout(withdrawalRequestId);
    }

    @Override
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public PayoutDecisionResponse confirmManualTransfer(
            UUID withdrawalRequestId,
            ManualTransferRequest requestPayload,
            MultipartFile proof
    ) {
        InternalAdminAccount admin = requireFinanceAdmin();
        PayoutProofStorageService.StoredProof storedProof =
                proofStorageService.store(withdrawalRequestId, proof);
        try {
            ManualFinalizeResult result = transactionTemplate.execute(status ->
                    confirmManualTransferInTransaction(
                            withdrawalRequestId,
                            requestPayload,
                            storedProof,
                            admin
                    )
            );
            if (result == null) {
                throw new BusinessException(
                        MessageCodes.COMMON_INTERNAL_ERROR,
                        "Manual payout confirmation did not return a result.",
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            if (!result.proofUsed()) {
                proofStorageService.deleteQuietly(storedProof.storageKey());
            }
            if (result.blockMessageCode() != null) {
                notifyFinanceAlert(
                        result.request(),
                        "Thanh toán thủ công bị chặn bởi đối soát",
                        result.blockMessage()
                );
                throw new BusinessException(
                        result.blockMessageCode(),
                        result.blockMessage(),
                        HttpStatus.CONFLICT
                );
            }
            if (result.notifyTeacher()) {
                notifyPayoutSucceeded(result.request(), result.settlement());
            }
            return toDecision(result.request(), result.settlement());
        } catch (RuntimeException exception) {
            proofStorageService.deleteQuietly(storedProof.storageKey());
            throw exception;
        }
    }

    @Override
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public PayoutDecisionResponse rejectPayout(
            UUID withdrawalRequestId,
            RejectPayoutRequest requestPayload
    ) {
        InternalAdminAccount admin = requireFinanceAdmin();
        String reason = requestPayload.getReason().trim();
        RejectionResult result = transactionTemplate.execute(
                status -> rejectPayoutInTransaction(withdrawalRequestId, reason, admin)
        );
        if (result == null) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Payout rejection did not return a result",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        if (!result.alreadyRejected()) {
            notifyPayoutRejected(result.request(), result.settlement(), reason);
        }
        return toDecision(result.request(), result.settlement());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public PayoutProofDownload getManualTransferProof(UUID withdrawalRequestId) {
        requireFinanceAdmin();
        PayoutSettlement settlement = payoutSettlementRepository
                .findByWithdrawalRequestId(withdrawalRequestId)
                .orElseThrow(() -> settlementNotFound(withdrawalRequestId));
        if (settlement.getTransferMethod() != PayoutTransferMethod.MANUAL
                || isBlank(settlement.getManualProofStorageKey())) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_PROOF_NOT_FOUND,
                    "This payout has no manual transfer proof.",
                    HttpStatus.NOT_FOUND
            );
        }
        Resource resource = proofStorageService.load(settlement.getManualProofStorageKey());
        return new PayoutProofDownload(
                resource,
                settlement.getManualProofOriginalName(),
                settlement.getManualProofContentType()
        );
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public PayoutProofDownload getBankQrCode(UUID withdrawalRequestId) {
        requireFinanceAdmin();
        WithdrawalRequest request = findRequest(withdrawalRequestId);
        byte[] qr = request.getBankQrCode();
        if (qr == null || qr.length == 0) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_BANK_QR_REQUIRED,
                    "This payout has no bank QR image.",
                    HttpStatus.NOT_FOUND
            );
        }
        String contentType = isBlank(request.getBankQrContentType())
                ? "image/png"
                : request.getBankQrContentType();
        String extension = "image/jpeg".equals(contentType)
                ? "jpg"
                : "image/webp".equals(contentType) ? "webp" : "png";
        return new PayoutProofDownload(
                new ByteArrayResource(qr),
                "bank-account-qr." + extension,
                contentType
        );
    }

    private PreparedPayout preparePayout(
            UUID withdrawalRequestId,
            InternalAdminAccount admin
    ) {
        WithdrawalRequest request = withdrawalRequestRepository.findByIdWithLock(withdrawalRequestId)
                .orElseThrow(() -> payoutNotFound(withdrawalRequestId));
        PayoutSettlement settlement = payoutSettlementRepository
                .findByWithdrawalRequestIdWithLock(withdrawalRequestId)
                .orElse(null);

        if (request.getStatus() == WithdrawalStatus.EXECUTED) {
            if (settlement != null && settlement.getStatus() == PayoutStatus.SUCCEEDED) {
                return new PreparedPayout(
                        request,
                        settlement,
                        request.getBankAccountSnapshot(),
                        PrepareMode.ALREADY_COMPLETED,
                        null,
                        null
                );
            }
            throw invalidStatus("The withdrawal is already executed.");
        }
        if (request.getStatus() == WithdrawalStatus.REJECTED
                || request.getStatus() == WithdrawalStatus.CANCELLED) {
            throw invalidStatus("The withdrawal cannot be approved from its current status.");
        }
        if (settlement != null && settlement.getStatus() == PayoutStatus.SUCCEEDED) {
            return new PreparedPayout(
                    request,
                    settlement,
                    request.getBankAccountSnapshot(),
                    PrepareMode.ALREADY_COMPLETED,
                    null,
                    null
            );
        }
        if (settlement != null && settlement.getStatus() == PayoutStatus.REJECTED) {
            throw invalidStatus("The payout settlement was rejected.");
        }

        OwnerContext owner = findOwner(request, true);
        Wallet wallet = owner.wallet();
        PayoutReconciliationService.ReconciliationResult reconciliation =
                reconcile(request, owner);

        if (reconciliation.hasCriticalMismatch()) {
            PayoutSettlement blocked = settlement == null
                    ? newSettlement(request, wallet, admin.getId())
                    : settlement;
            blocked.setStatus(PayoutStatus.FAILED);
            blocked.setReconciliationStatus(reconciliation.status());
            blocked.setReconciliationNote(reconciliationNote(reconciliation));
            blocked.setFailureCode(MessageCodes.MSG_ADM_005);
            blocked.setFailureMessageSanitized("Critical payout reconciliation mismatch.");
            blocked.setExecutedBy(admin.getId());
            payoutSettlementRepository.save(blocked);
            saveReconciliationLog(
                    request,
                    blocked,
                    wallet,
                    reconciliation,
                    admin.getId(),
                    "APPROVAL"
            );
            audit(
                    admin,
                    "PAYOUT_RECONCILIATION_BLOCKED",
                    request,
                    blocked,
                    request.getStatus().name(),
                    request.getStatus().name(),
                    null
            );

            boolean accountBlocked = reconciliation.teacherAccountBlocked();
            return new PreparedPayout(
                    request,
                    blocked,
                    request.getBankAccountSnapshot(),
                    PrepareMode.BLOCKED,
                    accountBlocked ? MessageCodes.PAYOUT_BALANCE_FROZEN : MessageCodes.MSG_ADM_005,
                    accountBlocked
                            ? "The teacher account is locked and cannot receive payouts."
                            : "Critical payout reconciliation mismatch."
            );
        }

        PrepareMode mode = PrepareMode.CALL_GATEWAY;
        if (settlement == null) {
            settlement = newSettlement(request, wallet, admin.getId());
        } else if (settlement.getStatus() == PayoutStatus.PROCESSING) {
            if (settlement.getProviderReferenceId() != null) {
                mode = PrepareMode.RESUME_PROVIDER_STATUS;
            } else if (!isStale(settlement.getProcessingStartedAt())) {
                throw new BusinessException(
                        MessageCodes.PAYOUT_SETTLEMENT_PROCESSING,
                        "The payout is being processed by another finance manager.",
                        HttpStatus.CONFLICT
                );
            } else {
                settlement.setRetryCount(settlement.getRetryCount() + 1);
            }
        } else if (settlement.getStatus() == PayoutStatus.FAILED
                || settlement.getStatus() == PayoutStatus.PENDING_RETRY) {
            settlement.setRetryCount(settlement.getRetryCount() + 1);
            if (settlement.getProviderReferenceId() != null) {
                mode = PrepareMode.RESUME_PROVIDER_STATUS;
            }
        } else {
            throw invalidStatus("The payout settlement cannot be processed.");
        }

        String statusBefore = request.getStatus().name();
        request.setStatus(WithdrawalStatus.APPROVED);
        request.setDecidedBy(admin.getId());
        request.setDecidedAt(LocalDateTime.now());

        settlement.setStatus(PayoutStatus.PROCESSING);
        settlement.setReconciliationStatus(reconciliation.status());
        settlement.setReconciliationNote(reconciliationNote(reconciliation));
        settlement.setDecision("APPROVED");
        settlement.setExecutedBy(admin.getId());
        settlement.setProcessingStartedAt(Instant.now());
        settlement.setFailureCode(null);
        settlement.setFailureMessageSanitized(null);

        withdrawalRequestRepository.save(request);
        payoutSettlementRepository.save(settlement);
        saveReconciliationLog(
                request,
                settlement,
                wallet,
                reconciliation,
                admin.getId(),
                "APPROVAL"
        );
        audit(
                admin,
                settlement.getRetryCount() > 0
                        ? "PAYOUT_RETRY_STARTED"
                        : "PAYOUT_APPROVAL_STARTED",
                request,
                settlement,
                statusBefore,
                request.getStatus().name(),
                null
        );

        return new PreparedPayout(
                request,
                settlement,
                request.getBankAccountSnapshot(),
                mode,
                null,
                null
        );
    }

    private PayoutGateway.PayoutGatewayResult invokeGateway(PreparedPayout prepared) {
        try {
            if (prepared.mode() == PrepareMode.RESUME_PROVIDER_STATUS) {
                return payoutGateway.getTransferStatus(
                        prepared.settlement().getProviderReferenceId()
                );
            }

            BankAccountSnapshot bank = prepared.bank();
            return payoutGateway.transfer(PayoutGateway.PayoutGatewayCommand.builder()
                    .settlementId(prepared.settlement().getId())
                    .amount(prepared.settlement().getAmount())
                    .currency(prepared.settlement().getCurrency())
                    .bankName(bank.getBankName())
                    .bankBranch(bank.getBranch())
                    .accountHolderName(bank.getAccountHolderName())
                    .accountNumber(
                            payoutSecurityService.decryptAccountNumber(bank.getAccountNumber())
                    )
                    .idempotencyKey(prepared.settlement().getIdempotencyKey())
                    .description("ManabiHub wallet payout")
                    .build());
        } catch (Exception exception) {
            log.warn(
                    "Payout provider call failed for settlement {}: {}",
                    prepared.settlement().getId(),
                    exception.getClass().getSimpleName()
            );
            return PayoutGateway.PayoutGatewayResult.builder()
                    .success(false)
                    .errorCode("PAYOUT_PROVIDER_UNAVAILABLE")
                    .errorMessage("The payout provider is temporarily unavailable.")
                    .isRetryable(true)
                    .build();
        }
    }

    private PayoutGateway.PayoutGatewayResult recordGatewayResult(
            UUID settlementId,
            PayoutGateway.PayoutGatewayResult gatewayResult,
            InternalAdminAccount admin
    ) {
        PayoutSettlement settlement = payoutSettlementRepository.findByIdWithLock(settlementId)
                .orElseThrow(() -> settlementNotFound(settlementId));
        WithdrawalRequest request = withdrawalRequestRepository
                .findByIdWithLock(settlement.getWithdrawalRequestId())
                .orElseThrow(() -> payoutNotFound(settlement.getWithdrawalRequestId()));

        if (settlement.getStatus() == PayoutStatus.SUCCEEDED) {
            return PayoutGateway.PayoutGatewayResult.builder()
                    .success(true)
                    .providerReference(settlement.getProviderReferenceId())
                    .build();
        }

        PayoutGateway.PayoutGatewayResult normalized = gatewayResult;
        if (gatewayResult.isSuccess()
                && isBlank(gatewayResult.getProviderReference())
                && !isBlank(settlement.getProviderReferenceId())) {
            normalized = PayoutGateway.PayoutGatewayResult.builder()
                    .success(true)
                    .providerReference(settlement.getProviderReferenceId())
                    .build();
        } else if (gatewayResult.isSuccess() && isBlank(gatewayResult.getProviderReference())) {
            normalized = PayoutGateway.PayoutGatewayResult.builder()
                    .success(false)
                    .errorCode("PAYOUT_PROVIDER_REFERENCE_MISSING")
                    .errorMessage("The payout provider did not return a transfer reference.")
                    .isRetryable(true)
                    .build();
        }

        settlement.setProvider(payoutGateway.providerName());
        if (normalized.isSuccess()) {
            settlement.setProviderReferenceId(normalized.getProviderReference());
            settlement.setFailureCode(null);
            settlement.setFailureMessageSanitized(null);
        } else {
            String statusBefore = request.getStatus().name();
            settlement.setStatus(normalized.isRetryable()
                    ? PayoutStatus.PENDING_RETRY
                    : PayoutStatus.FAILED);
            settlement.setFailureCode(sanitize(normalized.getErrorCode(), 100));
            settlement.setFailureMessageSanitized(sanitize(normalized.getErrorMessage(), 500));
            request.setStatus(WithdrawalStatus.FAILED);
            withdrawalRequestRepository.save(request);
            audit(
                    admin,
                    "PAYOUT_SETTLEMENT_FAILED",
                    request,
                    settlement,
                    statusBefore,
                    request.getStatus().name(),
                    settlement.getFailureCode()
            );
        }
        payoutSettlementRepository.save(settlement);
        return normalized;
    }

    private FinalizeResult finalizeSuccessfulPayout(
            UUID settlementId,
            InternalAdminAccount admin
    ) {
        PayoutSettlement settlement = payoutSettlementRepository.findByIdWithLock(settlementId)
                .orElseThrow(() -> settlementNotFound(settlementId));
        WithdrawalRequest request = withdrawalRequestRepository
                .findByIdWithLock(settlement.getWithdrawalRequestId())
                .orElseThrow(() -> payoutNotFound(settlement.getWithdrawalRequestId()));

        if (settlement.getStatus() == PayoutStatus.SUCCEEDED
                && request.getStatus() == WithdrawalStatus.EXECUTED) {
            return new FinalizeResult(request, settlement, false);
        }
        if (isBlank(settlement.getProviderReferenceId())) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_GATEWAY_FAILED,
                    "A verified payout provider reference is required before finalization.",
                    HttpStatus.CONFLICT
            );
        }

        OwnerContext owner = findOwner(request, true);
        Wallet wallet = owner.wallet();
        PayoutReconciliationService.ReconciliationResult reconciliation =
                reconcile(request, owner);
        if (reconciliation.hasCriticalMismatch()) {
            String statusBefore = request.getStatus().name();
            settlement.setStatus(PayoutStatus.PENDING_RETRY);
            settlement.setReconciliationStatus(reconciliation.status());
            settlement.setReconciliationNote(reconciliationNote(reconciliation));
            settlement.setFailureCode(MessageCodes.MSG_ADM_005);
            settlement.setFailureMessageSanitized(
                    "Provider transfer succeeded; local reconciliation blocks finalization."
            );
            request.setStatus(WithdrawalStatus.FAILED);
            withdrawalRequestRepository.save(request);
            payoutSettlementRepository.save(settlement);
            saveReconciliationLog(
                    request,
                    settlement,
                    wallet,
                    reconciliation,
                    admin.getId(),
                    "FINALIZATION"
            );
            audit(
                    admin,
                    "PAYOUT_RECONCILIATION_BLOCKED",
                    request,
                    settlement,
                    statusBefore,
                    request.getStatus().name(),
                    settlement.getProviderReferenceId()
            );
            return new FinalizeResult(request, settlement, true);
        }

        if (walletTransactionRepository
                .existsByReferenceTypeAndReferenceIdAndTransactionType(
                        WITHDRAWAL_REFERENCE,
                        request.getId(),
                        WalletTransactionType.WITHDRAWAL_COMPLETED
                )) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_DUPLICATE_SETTLEMENT,
                    "A payout ledger entry already exists for this withdrawal.",
                    HttpStatus.CONFLICT
            );
        }

        BigDecimal amount = request.getRequestedAmount();
        if (wallet.getBalance().compareTo(amount) < 0
                || wallet.getFrozenBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    MessageCodes.MSG_ADM_005,
                    "Wallet balance is inconsistent with the reserved withdrawal amount.",
                    HttpStatus.CONFLICT
            );
        }

        String statusBefore = request.getStatus().name();
        completeWalletWithdrawal(
                request,
                wallet,
                amount,
                "Payout settlement completed");

        request.setStatus(WithdrawalStatus.EXECUTED);
        withdrawalRequestRepository.save(request);

        settlement.setStatus(PayoutStatus.SUCCEEDED);
        settlement.setReconciliationStatus(reconciliation.status());
        settlement.setReconciliationNote(reconciliationNote(reconciliation));
        settlement.setDecision("APPROVED");
        settlement.setExecutedBy(admin.getId());
        settlement.setExecutedAt(Instant.now());
        settlement.setFailureCode(null);
        settlement.setFailureMessageSanitized(null);
        settlement.setNotificationStatus(PayoutNotificationStatus.PENDING);
        payoutSettlementRepository.save(settlement);
        saveReconciliationLog(
                request,
                settlement,
                wallet,
                reconciliation,
                admin.getId(),
                "FINALIZATION"
        );
        audit(
                admin,
                "PAYOUT_SETTLEMENT_SUCCEEDED",
                request,
                settlement,
                statusBefore,
                request.getStatus().name(),
                settlement.getProviderReferenceId()
        );

        return new FinalizeResult(request, settlement, false);
    }

    private ManualFinalizeResult confirmManualTransferInTransaction(
            UUID withdrawalRequestId,
            ManualTransferRequest payload,
            PayoutProofStorageService.StoredProof proof,
            InternalAdminAccount admin
    ) {
        WithdrawalRequest request = withdrawalRequestRepository.findByIdWithLock(withdrawalRequestId)
                .orElseThrow(() -> payoutNotFound(withdrawalRequestId));
        PayoutSettlement settlement = payoutSettlementRepository
                .findByWithdrawalRequestIdWithLock(withdrawalRequestId)
                .orElse(null);
        String reference = payload.getTransactionReference().trim();

        if (request.getStatus() == WithdrawalStatus.EXECUTED
                && settlement != null
                && settlement.getStatus() == PayoutStatus.SUCCEEDED
                && settlement.getTransferMethod() == PayoutTransferMethod.MANUAL
                && reference.equals(settlement.getProviderReferenceId())) {
            return new ManualFinalizeResult(
                    request,
                    settlement,
                    false,
                    false,
                    null,
                    null
            );
        }
        if (request.getStatus() == WithdrawalStatus.EXECUTED
                || request.getStatus() == WithdrawalStatus.REJECTED
                || request.getStatus() == WithdrawalStatus.CANCELLED) {
            throw invalidStatus("The withdrawal cannot be manually settled from its current status.");
        }
        if (payload.getTransferredAmount().compareTo(request.getRequestedAmount()) != 0) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_MANUAL_AMOUNT_MISMATCH,
                    "Transferred amount must exactly match the withdrawal amount.",
                    HttpStatus.CONFLICT
            );
        }
        if (settlement != null
                && (settlement.getStatus() == PayoutStatus.PROCESSING
                    || settlement.getStatus() == PayoutStatus.SUCCEEDED
                    || !isBlank(settlement.getProviderReferenceId()))) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_SETTLEMENT_PROCESSING,
                    "A provider transfer has already started for this withdrawal.",
                    HttpStatus.CONFLICT
            );
        }
        if (payoutSettlementRepository.existsByProviderAndProviderReferenceId(
                "MANUAL_BANK_TRANSFER",
                reference
        )) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_MANUAL_REFERENCE_DUPLICATE,
                    "The bank transaction reference has already been used.",
                    HttpStatus.CONFLICT
            );
        }

        OwnerContext owner = findOwner(request, true);
        Wallet wallet = owner.wallet();
        PayoutReconciliationService.ReconciliationResult reconciliation =
                reconcile(request, owner);

        if (settlement == null) {
            settlement = newSettlement(request, wallet, admin.getId());
        }
        settlement.setTransferMethod(PayoutTransferMethod.MANUAL);
        settlement.setExecutedBy(admin.getId());
        settlement.setReconciliationStatus(reconciliation.status());
        settlement.setReconciliationNote(reconciliationNote(reconciliation));

        if (reconciliation.hasCriticalMismatch()) {
            settlement.setStatus(PayoutStatus.FAILED);
            settlement.setFailureCode(MessageCodes.MSG_ADM_005);
            settlement.setFailureMessageSanitized("Manual payout blocked by reconciliation.");
            payoutSettlementRepository.save(settlement);
            saveReconciliationLog(
                    request,
                    settlement,
                    wallet,
                    reconciliation,
                    admin.getId(),
                    "MANUAL_TRANSFER"
            );
            audit(
                    admin,
                    "PAYOUT_RECONCILIATION_BLOCKED",
                    request,
                    settlement,
                    request.getStatus().name(),
                    request.getStatus().name(),
                    reference
            );
            return new ManualFinalizeResult(
                    request,
                    settlement,
                    false,
                    false,
                    MessageCodes.MSG_ADM_005,
                    "Sai lệch đối soát nghiêm trọng đang chặn việc xác nhận thanh toán thủ công."
            );
        }

        if (walletTransactionRepository
                .existsByReferenceTypeAndReferenceIdAndTransactionType(
                        WITHDRAWAL_REFERENCE,
                        request.getId(),
                        WalletTransactionType.WITHDRAWAL_COMPLETED
                )) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_DUPLICATE_SETTLEMENT,
                    "A payout ledger entry already exists for this withdrawal.",
                    HttpStatus.CONFLICT
            );
        }
        BigDecimal amount = request.getRequestedAmount();
        if (wallet.getBalance().compareTo(amount) < 0
                || wallet.getFrozenBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    MessageCodes.MSG_ADM_005,
                    "Wallet balance is inconsistent with the reserved withdrawal amount.",
                    HttpStatus.CONFLICT
            );
        }

        // Persist the exact pre-debit evidence used to authorize this manual
        // settlement. A post-debit snapshot would no longer match the
        // reconciliation result and would weaken the audit trail.
        payoutSettlementRepository.save(settlement);
        saveReconciliationLog(
                request,
                settlement,
                wallet,
                reconciliation,
                admin.getId(),
                "MANUAL_TRANSFER"
        );

        String statusBefore = request.getStatus().name();
        completeWalletWithdrawal(
                request,
                wallet,
                amount,
                "Manual payout settlement completed");

        request.setStatus(WithdrawalStatus.EXECUTED);
        request.setDecidedBy(admin.getId());
        request.setDecidedAt(LocalDateTime.now());
        withdrawalRequestRepository.save(request);

        settlement.setStatus(PayoutStatus.SUCCEEDED);
        settlement.setDecision("APPROVED");
        settlement.setProvider("MANUAL_BANK_TRANSFER");
        settlement.setProviderReferenceId(reference);
        settlement.setDecisionReason(payload.getNote() == null ? null : payload.getNote().trim());
        settlement.setManualProofStorageKey(proof.storageKey());
        settlement.setManualProofOriginalName(proof.originalName());
        settlement.setManualProofContentType(proof.contentType());
        settlement.setManualProofSize(proof.size());
        settlement.setManualTransferredAt(payload.getTransferredAt());
        settlement.setExecutedAt(Instant.now());
        settlement.setFailureCode(null);
        settlement.setFailureMessageSanitized(null);
        settlement.setNotificationStatus(PayoutNotificationStatus.PENDING);
        payoutSettlementRepository.save(settlement);
        audit(
                admin,
                "PAYOUT_MANUAL_TRANSFER_CONFIRMED",
                request,
                settlement,
                statusBefore,
                request.getStatus().name(),
                reference
        );
        return new ManualFinalizeResult(request, settlement, true, true, null, null);
    }

    private RejectionResult rejectPayoutInTransaction(
            UUID withdrawalRequestId,
            String reason,
            InternalAdminAccount admin
    ) {
        WithdrawalRequest request = withdrawalRequestRepository.findByIdWithLock(withdrawalRequestId)
                .orElseThrow(() -> payoutNotFound(withdrawalRequestId));
        PayoutSettlement settlement = payoutSettlementRepository
                .findByWithdrawalRequestIdWithLock(withdrawalRequestId)
                .orElse(null);

        if (request.getStatus() == WithdrawalStatus.REJECTED) {
            if (settlement == null || settlement.getStatus() != PayoutStatus.REJECTED) {
                throw invalidStatus("The rejected withdrawal has no matching settlement decision.");
            }
            return new RejectionResult(request, settlement, true);
        }
        if (request.getStatus() == WithdrawalStatus.EXECUTED
                || request.getStatus() == WithdrawalStatus.CANCELLED) {
            throw invalidStatus("The withdrawal can no longer be rejected.");
        }
        if (settlement != null
                && (settlement.getStatus() == PayoutStatus.SUCCEEDED
                || settlement.getStatus() == PayoutStatus.PROCESSING
                || settlement.getProviderReferenceId() != null)) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_SETTLEMENT_PROCESSING,
                    "A payout transfer has started and cannot be rejected safely.",
                    HttpStatus.CONFLICT
            );
        }

        OwnerContext owner = findOwner(request, true);
        Wallet wallet = owner.wallet();
        BigDecimal amount = request.getRequestedAmount();
        if (wallet.getFrozenBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    MessageCodes.MSG_ADM_005,
                    "Reserved balance is lower than the withdrawal amount.",
                    HttpStatus.CONFLICT
            );
        }
        boolean reservationExists = walletTransactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        WITHDRAWAL_REFERENCE,
                        request.getId(),
                        WalletTransactionType.WITHDRAWAL_RESERVATION
                )
                .filter(transaction -> wallet.getId().equals(transaction.getWalletId()))
                .filter(transaction -> transaction.getDirection() == WalletDirection.OUT)
                .filter(transaction -> transaction.getAmount() != null)
                .filter(transaction -> transaction.getAmount().abs().compareTo(amount) == 0)
                .isPresent();
        if (!reservationExists) {
            throw new BusinessException(
                    MessageCodes.MSG_ADM_005,
                    "The withdrawal reservation ledger is missing or inconsistent.",
                    HttpStatus.CONFLICT
            );
        }

        if (!walletTransactionRepository
                .existsByReferenceTypeAndReferenceIdAndTransactionType(
                        WITHDRAWAL_REFERENCE,
                        request.getId(),
                        WalletTransactionType.WITHDRAWAL_REJECTED
                )) {
            releaseWalletWithdrawal(
                    request,
                    wallet,
                    amount,
                    WalletTransactionType.WITHDRAWAL_REJECTED,
                    "Withdrawal reservation released after finance rejection");
        }

        String statusBefore = request.getStatus().name();
        request.setStatus(WithdrawalStatus.REJECTED);
        request.setDecidedBy(admin.getId());
        request.setDecisionNote(reason);
        request.setDecidedAt(LocalDateTime.now());
        withdrawalRequestRepository.save(request);

        if (settlement == null) {
            settlement = newSettlement(request, wallet, admin.getId());
        }
        settlement.setStatus(PayoutStatus.REJECTED);
        settlement.setDecision("REJECTED");
        settlement.setDecisionReason(reason);
        settlement.setExecutedBy(admin.getId());
        settlement.setExecutedAt(Instant.now());
        settlement.setNotificationStatus(PayoutNotificationStatus.PENDING);
        PayoutReconciliationService.ReconciliationResult reconciliation =
                reconciliationService.reconcileRejected(request, wallet, settlement);
        settlement.setReconciliationStatus(reconciliation.status());
        settlement.setReconciliationNote(reconciliationNote(reconciliation));
        payoutSettlementRepository.save(settlement);
        audit(
                admin,
                "PAYOUT_REJECTED",
                request,
                settlement,
                statusBefore,
                request.getStatus().name(),
                reason
        );
        return new RejectionResult(request, settlement, false);
    }

    private PayoutQueueItemResponse toQueueItem(WithdrawalRequest request) {
        OwnerContext owner = findOwner(request, false);
        BankAccountSnapshot bank = request.getBankAccountSnapshot();
        PayoutSettlement settlement = payoutSettlementRepository
                .findByWithdrawalRequestId(request.getId())
                .orElse(null);
        PayoutReconciliationService.ReconciliationResult reconciliation =
                reconcileForCurrentPhase(request, owner, settlement);

        return PayoutQueueItemResponse.builder()
                .withdrawalRequestId(request.getId())
                .walletId(request.getWalletId())
                .ownerType(owner.ownerType())
                .ownerId(owner.profileId())
                .ownerName(owner.name())
                .teacherId(request.getTeacherId())
                .teacherName(owner.name())
                .requestedAmount(request.getRequestedAmount())
                .bankName(bank == null ? null : bank.getBankName())
                .accountNumberMasked(bank == null
                        ? null
                        : payoutSecurityService.maskAccountNumber(bank.getAccountNumber()))
                .status(request.getStatus())
                .settlementStatus(settlement == null ? null : settlement.getStatus())
                .reconciliationStatus(reconciliation.status())
                .requestedAt(request.getRequestedAt())
                .processingStartedAt(settlement == null ? null : settlement.getProcessingStartedAt())
                .provider(settlement == null ? null : settlement.getProvider())
                .providerReference(settlement == null ? null : settlement.getProviderReferenceId())
                .decidedBy(request.getDecidedBy())
                .decidedAt(request.getDecidedAt())
                .updatedAt(request.getUpdatedAt())
                .settlementUpdatedAt(settlement == null ? null : settlement.getUpdatedAt())
                .retryCount(settlement == null ? 0 : settlement.getRetryCount())
                .build();
    }

    private PayoutDetailResponse toDetail(
            WithdrawalRequest request,
            OwnerContext owner,
            PayoutSettlement settlement,
            PayoutReconciliationService.ReconciliationResult reconciliation
    ) {
        Wallet wallet = owner.wallet();
        BankAccountSnapshot bank = request.getBankAccountSnapshot();
        return PayoutDetailResponse.builder()
                .withdrawalRequestId(request.getId())
                .settlementId(settlement == null ? null : settlement.getId())
                .ownerType(owner.ownerType())
                .ownerId(owner.profileId())
                .ownerName(owner.name())
                .ownerAccountStatus(owner.accountStatus())
                .teacherId(request.getTeacherId())
                .teacherName(owner.name())
                .teacherAccountStatus(owner.accountStatus())
                .requestedAmount(request.getRequestedAmount())
                .availableBalance(wallet.getAvailableBalance())
                .reservedBalance(wallet.getFrozenBalance())
                .pendingClearing(reconciliation.pendingClearing())
                .walletFrozen(wallet.isFrozen())
                .escrowStatus(reconciliation.escrowStatus())
                .status(request.getStatus())
                .settlementStatus(settlement == null ? null : settlement.getStatus())
                .reconciliationStatus(reconciliation.status())
                .reconciliationAlerts(reconciliation.alerts().stream()
                        .map(alert -> ReconciliationAlertResponse.builder()
                                .code(alert.code())
                                .severity(alert.severity())
                                .message(alert.message())
                                .build())
                        .toList())
                .bankName(bank == null ? null : bank.getBankName())
                .bankBranch(bank == null ? null : bank.getBranch())
                .accountHolderName(bank == null ? null : bank.getAccountHolderName())
                .accountNumberMasked(
                        bank == null
                                ? null
                                : payoutSecurityService.maskAccountNumber(bank.getAccountNumber())
                )
                .requestedAt(request.getRequestedAt())
                .processingStartedAt(settlement == null ? null : settlement.getProcessingStartedAt())
                .settledAt(settlement == null ? null : settlement.getExecutedAt())
                .decision(settlement == null ? null : settlement.getDecision())
                .decisionReason(settlement == null ? null : settlement.getDecisionReason())
                .gatewayProvider(settlement == null ? null : settlement.getProvider())
                .gatewayReference(settlement == null ? null : settlement.getProviderReferenceId())
                .transferMethod(settlement == null ? null : settlement.getTransferMethod())
                .manualProofAvailable(settlement != null
                        && !isBlank(settlement.getManualProofStorageKey()))
                .bankQrAvailable(request.getBankQrCode() != null && request.getBankQrCode().length > 0)
                .manualProofOriginalName(settlement == null
                        ? null
                        : settlement.getManualProofOriginalName())
                .manualProofSize(settlement == null ? null : settlement.getManualProofSize())
                .manualTransferredAt(settlement == null
                        ? null
                        : settlement.getManualTransferredAt())
                .failureCode(settlement == null ? null : settlement.getFailureCode())
                .failureMessage(settlement == null ? null : settlement.getFailureMessageSanitized())
                .retryCount(settlement == null ? 0 : settlement.getRetryCount())
                .notificationStatus(settlement == null
                        ? PayoutNotificationStatus.NOT_REQUIRED
                        : settlement.getNotificationStatus())
                .notificationAttempts(settlement == null
                        ? 0
                        : settlement.getNotificationAttempts())
                .reconciliationHistory(reconciliationHistory(request.getId()))
                .build();
    }

    private void saveReconciliationLog(
            WithdrawalRequest request,
            PayoutSettlement settlement,
            Wallet wallet,
            PayoutReconciliationService.ReconciliationResult reconciliation,
            UUID checkedBy,
            String triggerType
    ) {
        List<Map<String, Object>> alertSnapshot = reconciliation.alerts().stream()
                .map(alert -> Map.<String, Object>of(
                        "code", alert.code(),
                        "severity", alert.severity(),
                        "message", alert.message()
                ))
                .toList();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("ownerType", request.getOwnerType().name());
        evidence.put("ownerId", ownerProfileId(request));
        evidence.put("walletId", wallet.getId());
        evidence.put("requestedAmount", request.getRequestedAmount());
        evidence.put("walletBalance", wallet.getBalance());
        evidence.put("reservedBalance", wallet.getFrozenBalance());
        evidence.put("availableBalance", wallet.getAvailableBalance());
        evidence.put("walletFrozen", wallet.isFrozen());
        evidence.put("currency", wallet.getCurrency());
        evidence.put("pendingClearing", reconciliation.pendingClearing());
        evidence.put("escrowStatus", reconciliation.escrowStatus());
        evidence.put("teacherAccountBlocked", reconciliation.teacherAccountBlocked());

        reconciliationLogRepository.save(PayoutReconciliationLog.builder()
                .withdrawalRequestId(request.getId())
                .payoutSettlementId(settlement == null ? null : settlement.getId())
                .checkedBy(checkedBy)
                .triggerType(triggerType)
                .status(reconciliation.status())
                .alerts(alertSnapshot)
                .evidenceSnapshot(evidence)
                .build());
    }

    private List<ReconciliationHistoryResponse> reconciliationHistory(UUID withdrawalRequestId) {
        return reconciliationLogRepository
                .findByWithdrawalRequestIdOrderByCreatedAtDesc(
                        withdrawalRequestId,
                        PageRequest.of(0, 20)
                )
                .stream()
                .map(logEntry -> ReconciliationHistoryResponse.builder()
                        .id(logEntry.getId())
                        .triggerType(logEntry.getTriggerType())
                        .status(logEntry.getStatus())
                        .checkedBy(logEntry.getCheckedBy())
                        .createdAt(logEntry.getCreatedAt())
                        .alerts(logEntry.getAlerts().stream()
                                .map(alert -> ReconciliationAlertResponse.builder()
                                        .code(String.valueOf(alert.get("code")))
                                        .severity(String.valueOf(alert.get("severity")))
                                        .message(String.valueOf(alert.get("message")))
                                        .build())
                                .toList())
                        .build())
                .toList();
    }

    private InternalAdminAccount requireFinanceAdmin() {
        UUID adminId = currentUserService.getCurrentUserId();
        InternalAdminAccount admin = internalAdminAccountRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.PAYOUT_PERMISSION_DENIED,
                        "Finance Manager account was not found.",
                        HttpStatus.FORBIDDEN
                ));
        if (admin.getAccountStatus() != AccountStatus.ACTIVE
                || admin.getRole() == null
                || admin.getRole().getCode() != RoleCode.FINANCE_MANAGER) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_PERMISSION_DENIED,
                    "Finance Manager permission is required.",
                    HttpStatus.FORBIDDEN
            );
        }
        return admin;
    }

    private PayoutSettlement newSettlement(
            WithdrawalRequest request,
            Wallet wallet,
            UUID adminId
    ) {
        return PayoutSettlement.builder()
                .withdrawalRequestId(request.getId())
                .ownerType(request.getOwnerType())
                .teacherId(request.getTeacherId())
                .studentId(request.getStudentId())
                .walletId(wallet.getId())
                .amount(request.getRequestedAmount())
                .currency(wallet.getCurrency())
                .status(PayoutStatus.PROCESSING)
                .idempotencyKey("payout-" + request.getId())
                .reconciliationStatus(ReconciliationStatus.WARNING)
                .transferMethod(PayoutTransferMethod.GATEWAY)
                .executedBy(adminId)
                .processingStartedAt(Instant.now())
                .build();
    }

    private PayoutDecisionResponse toDecision(
            WithdrawalRequest request,
            PayoutSettlement settlement
    ) {
        return PayoutDecisionResponse.builder()
                .withdrawalRequestId(request.getId())
                .settlementId(settlement.getId())
                .withdrawalStatus(request.getStatus())
                .settlementStatus(settlement.getStatus())
                .reconciliationStatus(settlement.getReconciliationStatus())
                .transferMethod(settlement.getTransferMethod())
                .gatewayReference(settlement.getProviderReferenceId())
                .settledAt(settlement.getExecutedAt())
                .notificationStatus(settlement.getNotificationStatus())
                .build();
    }

    private void audit(
            InternalAdminAccount admin,
            String action,
            WithdrawalRequest request,
            PayoutSettlement settlement,
            String statusBefore,
            String statusAfter,
            String detail
    ) {
        auditLogService.logAdminAction(
                admin.getId(),
                FINANCE_ROLE,
                action,
                "WITHDRAWAL_REQUEST",
                request.getId(),
                Map.of("status", statusBefore),
                Map.of("status", statusAfter),
                Map.of(
                        "payoutSettlementId", settlement.getId(),
                        "ownerType", request.getOwnerType().name(),
                        "ownerId", ownerProfileId(request),
                        "walletId", settlement.getWalletId(),
                        "amount", request.getRequestedAmount(),
                        "reconciliationStatus", settlement.getReconciliationStatus().name(),
                        "detail", detail == null ? "" : detail
                )
        );
    }

    private void notifyPayoutSucceeded(
            WithdrawalRequest request,
            PayoutSettlement settlement
    ) {
        boolean sent = false;
        try {
            NotificationTarget target = ownerNotificationTarget(request);
            notificationService.createNotification(
                    target.userId(),
                    target.email(),
                    "Thanh toán doanh thu thành công",
                    "Yêu cầu rút " + request.getRequestedAmount()
                            + " VND đã được thanh toán tới tài khoản "
                            + payoutSecurityService.maskAccountNumber(
                                    request.getBankAccountSnapshot().getAccountNumber()
                            )
                            + ". Mã đối soát: " + settlement.getProviderReferenceId(),
                    NotificationTypes.PAYOUT_SUCCESS,
                    target.walletLink()
            );
            sent = true;
        } catch (Exception exception) {
            log.warn(
                    "Payout {} succeeded but its notification could not be created: {}",
                    settlement.getId(),
                    exception.getClass().getSimpleName()
            );
        } finally {
            recordNotificationResult(settlement, sent);
        }
    }

    private void notifyGatewayFailure(
            WithdrawalRequest request,
            PayoutGateway.PayoutGatewayResult result
    ) {
        try {
            NotificationTarget target = ownerNotificationTarget(request);
            notificationService.createNotification(
                    target.userId(),
                    target.email(),
                    result.isRetryable()
                            ? "Yêu cầu rút tiền đang được xử lý lại"
                            : "Thanh toán doanh thu chưa thành công",
                    result.isRetryable()
                            ? "Yêu cầu rút tiền đang chờ hệ thống xử lý lại. Bạn không cần tạo yêu cầu mới."
                            : "Yêu cầu rút tiền chưa thể thanh toán. Số tiền đã giữ vẫn được bảo toàn.",
                    result.isRetryable()
                            ? NotificationTypes.PAYOUT_PENDING_RETRY
                            : NotificationTypes.PAYOUT_FAILED,
                    target.walletLink()
            );
        } catch (Exception exception) {
            log.warn("Gateway failure notification could not be created for withdrawal {}.", request.getId());
        }
    }

    private void notifyPayoutRejected(
            WithdrawalRequest request,
            PayoutSettlement settlement,
            String reason
    ) {
        boolean sent = false;
        try {
            NotificationTarget target = ownerNotificationTarget(request);
            notificationService.createNotification(
                    target.userId(),
                    target.email(),
                    "Yêu cầu rút tiền bị từ chối",
                    "Yêu cầu rút tiền đã bị từ chối. Lý do: " + reason
                            + ". Số tiền đã giữ được trả lại số dư khả dụng.",
                    NotificationTypes.PAYOUT_REJECTED,
                    target.walletLink()
            );
            sent = true;
        } catch (Exception exception) {
            log.warn("Rejection notification could not be created for withdrawal {}.", request.getId());
        } finally {
            recordNotificationResult(settlement, sent);
        }
    }

    private void recordNotificationResult(PayoutSettlement settlement, boolean sent) {
        try {
            settlement.setNotificationAttempts(settlement.getNotificationAttempts() + 1);
            settlement.setNotificationStatus(sent
                    ? PayoutNotificationStatus.SENT
                    : PayoutNotificationStatus.FAILED);
            payoutSettlementRepository.save(settlement);
        } catch (Exception exception) {
            log.warn(
                    "Could not record notification result for payout settlement {}.",
                    settlement.getId()
            );
        }
    }

    private void notifyFinanceAlert(
            WithdrawalRequest request,
            String title,
            String message
    ) {
        try {
            notificationService.createNotificationForAdminRole(
                    FINANCE_ROLE,
                    title,
                    message,
                    NotificationTypes.PAYOUT_ALERT,
                    "/admin/payouts/" + request.getId()
            );
        } catch (Exception exception) {
            log.warn("Finance alert could not be created for withdrawal {}.", request.getId());
        }
    }

    private OwnerContext findOwner(WithdrawalRequest request, boolean lockWallet) {
        WalletOwnerType ownerType = request.getOwnerType() == null
                ? request.getStudentId() == null
                    ? WalletOwnerType.TEACHER
                    : WalletOwnerType.STUDENT
                : request.getOwnerType();
        if (ownerType == WalletOwnerType.STUDENT) {
            UUID studentId = request.getStudentId();
            StudentProfile student = studentProfileRepository.findById(studentId)
                    .orElseThrow(() -> new BusinessException(
                            MessageCodes.PAYOUT_RECONCILIATION_MISMATCH,
                            "Student profile was not found.",
                            HttpStatus.CONFLICT));
            Wallet wallet = lockWallet
                    ? walletRepository.findByOwnerTypeAndStudent_IdForUpdate(
                            WalletOwnerType.STUDENT, studentId)
                        .orElseThrow(() -> walletNotFound(studentId))
                    : walletRepository.findByOwnerTypeAndStudent_Id(
                            WalletOwnerType.STUDENT, studentId)
                        .orElseThrow(() -> walletNotFound(studentId));
            String name = !isBlank(student.getDisplayName())
                    ? student.getDisplayName()
                    : student.getUser() != null && !isBlank(student.getUser().getFullName())
                        ? student.getUser().getFullName()
                        : "Student";
            String accountStatus = student.getUser() == null
                    ? "UNKNOWN"
                    : student.getUser().getUserStatus().name();
            return new OwnerContext(
                    ownerType,
                    studentId,
                    student.getUser() == null ? null : student.getUser().getId(),
                    name,
                    accountStatus,
                    wallet,
                    null,
                    student);
        }

        TeacherProfile teacher = findTeacher(request.getTeacherId());
        Wallet wallet = lockWallet
                ? walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(
                        WalletOwnerType.TEACHER, request.getTeacherId())
                    .orElseThrow(() -> walletNotFound(request.getTeacherId()))
                : findWallet(request.getTeacherId());
        return new OwnerContext(
                WalletOwnerType.TEACHER,
                request.getTeacherId(),
                teacher.getUser() == null ? null : teacher.getUser().getId(),
                teacherName(teacher),
                teacher.getUser() == null
                        ? "UNKNOWN"
                        : teacher.getUser().getUserStatus().name(),
                wallet,
                teacher,
                null);
    }

    private PayoutReconciliationService.ReconciliationResult reconcile(
            WithdrawalRequest request,
            OwnerContext owner
    ) {
        return owner.ownerType() == WalletOwnerType.STUDENT
                ? reconciliationService.reconcileStudent(
                        request, owner.wallet(), owner.student())
                : reconciliationService.reconcile(
                        request, owner.wallet(), owner.teacher());
    }

    private PayoutReconciliationService.ReconciliationResult reconcileForCurrentPhase(
            WithdrawalRequest request,
            OwnerContext owner,
            PayoutSettlement settlement
    ) {
        boolean rejectionPhase = request.getStatus() == WithdrawalStatus.REJECTED
                || settlement != null && settlement.getStatus() == PayoutStatus.REJECTED;
        if (rejectionPhase) {
            return reconciliationService.reconcileRejected(
                    request,
                    owner.wallet(),
                    settlement
            );
        }
        boolean completionPhase = request.getStatus() == WithdrawalStatus.EXECUTED
                || settlement != null && settlement.getStatus() == PayoutStatus.SUCCEEDED;
        if (completionPhase) {
            return reconciliationService.reconcileCompleted(
                    request,
                    owner.wallet(),
                    settlement
            );
        }
        return reconcile(request, owner);
    }

    private UUID ownerProfileId(WithdrawalRequest request) {
        return request.getOwnerType() == WalletOwnerType.STUDENT
                ? request.getStudentId()
                : request.getTeacherId();
    }

    /**
     * Resolves who the payout notification belongs to. Students and teachers own
     * withdrawals through different profiles and land on different wallet pages,
     * so the recipient, their email and the deep link are resolved together.
     */
    private NotificationTarget ownerNotificationTarget(WithdrawalRequest request) {
        UUID userId = null;
        String email = null;
        String walletLink;
        // Student and teacher profiles expose different AppUser types, so each
        // branch reads the identity it owns rather than sharing a variable.
        if (request.getOwnerType() == WalletOwnerType.STUDENT) {
            StudentProfile student = studentProfileRepository.findById(request.getStudentId())
                    .orElseThrow(() -> payoutNotFound(request.getId()));
            if (student.getUser() != null) {
                userId = student.getUser().getId();
                email = student.getUser().getEmail();
            }
            walletLink = STUDENT_WALLET_LINK;
        } else {
            TeacherProfile teacher = findTeacher(request.getTeacherId());
            if (teacher.getUser() != null) {
                userId = teacher.getUser().getId();
                email = teacher.getUser().getEmail();
            }
            walletLink = TEACHER_WALLET_LINK;
        }
        if (userId == null) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_RECONCILIATION_MISMATCH,
                    "Withdrawal owner user was not found.",
                    HttpStatus.CONFLICT);
        }
        return new NotificationTarget(userId, email, walletLink);
    }

    private record NotificationTarget(UUID userId, String email, String walletLink) {
    }

    private void completeWalletWithdrawal(
            WithdrawalRequest request,
            Wallet wallet,
            BigDecimal amount,
            String note
    ) {
        if (request.getOwnerType() == WalletOwnerType.STUDENT) {
            studentWalletService.completeWithdrawal(
                    request.getStudentId(), request.getId(), amount);
            return;
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setFrozenBalance(wallet.getFrozenBalance().subtract(amount));
        walletRepository.save(wallet);
        walletTransactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(WalletTransactionType.WITHDRAWAL_COMPLETED)
                .amount(amount)
                .direction(WalletDirection.OUT)
                .referenceType(WITHDRAWAL_REFERENCE)
                .referenceId(request.getId())
                .note(note)
                .build());
    }

    private void releaseWalletWithdrawal(
            WithdrawalRequest request,
            Wallet wallet,
            BigDecimal amount,
            WalletTransactionType releaseType,
            String note
    ) {
        if (request.getOwnerType() == WalletOwnerType.STUDENT) {
            studentWalletService.releaseWithdrawal(
                    request.getStudentId(), request.getId(), amount, releaseType, note);
            return;
        }
        wallet.setFrozenBalance(wallet.getFrozenBalance().subtract(amount));
        walletRepository.save(wallet);
        walletTransactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(releaseType)
                .amount(amount)
                .direction(WalletDirection.IN)
                .referenceType(WITHDRAWAL_REFERENCE)
                .referenceId(request.getId())
                .note(note)
                .build());
    }

    private WithdrawalRequest findRequest(UUID id) {
        return withdrawalRequestRepository.findById(id)
                .orElseThrow(() -> payoutNotFound(id));
    }

    private TeacherProfile findTeacher(UUID teacherId) {
        return teacherProfileRepository.findById(teacherId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.PAYOUT_RECONCILIATION_MISMATCH,
                        "Teacher profile was not found.",
                        HttpStatus.CONFLICT
                ));
    }

    private Wallet findWallet(UUID teacherId) {
        return walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacherId)
                .orElseThrow(() -> walletNotFound(teacherId));
    }

    private BusinessException payoutNotFound(UUID id) {
        return new BusinessException(
                MessageCodes.PAYOUT_NOT_FOUND,
                "Withdrawal request " + id + " was not found.",
                HttpStatus.NOT_FOUND
        );
    }

    private BusinessException settlementNotFound(UUID id) {
        return new BusinessException(
                MessageCodes.PAYOUT_NOT_FOUND,
                "Payout settlement " + id + " was not found.",
                HttpStatus.NOT_FOUND
        );
    }

    private BusinessException walletNotFound(UUID teacherId) {
        return new BusinessException(
                MessageCodes.WALLET_NOT_FOUND,
                "Teacher wallet for " + teacherId + " was not found.",
                HttpStatus.CONFLICT
        );
    }

    private BusinessException invalidStatus(String message) {
        return new BusinessException(
                MessageCodes.PAYOUT_INVALID_STATUS,
                message,
                HttpStatus.CONFLICT
        );
    }

    private String teacherName(TeacherProfile teacher) {
        if (!isBlank(teacher.getDisplayName())) {
            return teacher.getDisplayName();
        }
        if (teacher.getUser() != null && !isBlank(teacher.getUser().getFullName())) {
            return teacher.getUser().getFullName();
        }
        return "Teacher";
    }

    private String reconciliationNote(
            PayoutReconciliationService.ReconciliationResult reconciliation
    ) {
        return sanitize(
                reconciliation.alerts().stream()
                        .map(PayoutReconciliationService.ReconciliationAlert::code)
                        .reduce((left, right) -> left + "," + right)
                        .orElse("MATCHED"),
                500
        );
    }

    private boolean isStale(Instant processingStartedAt) {
        return processingStartedAt == null
                || processingStartedAt.plus(PROCESSING_STALE_AFTER).isBefore(Instant.now());
    }

    private String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return sanitized.length() <= maxLength
                ? sanitized
                : sanitized.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private enum PrepareMode {
        CALL_GATEWAY,
        RESUME_PROVIDER_STATUS,
        ALREADY_COMPLETED,
        BLOCKED
    }

    private record PreparedPayout(
            WithdrawalRequest request,
            PayoutSettlement settlement,
            BankAccountSnapshot bank,
            PrepareMode mode,
            String blockMessageCode,
            String blockMessage
    ) {
    }

    private record FinalizeResult(
            WithdrawalRequest request,
            PayoutSettlement settlement,
            boolean blocked
    ) {
    }

    private record ManualFinalizeResult(
            WithdrawalRequest request,
            PayoutSettlement settlement,
            boolean proofUsed,
            boolean notifyTeacher,
            String blockMessageCode,
            String blockMessage
    ) {
    }

    private record RejectionResult(
            WithdrawalRequest request,
            PayoutSettlement settlement,
            boolean alreadyRejected
    ) {
    }

    private record OwnerContext(
            WalletOwnerType ownerType,
            UUID profileId,
            UUID userId,
            String name,
            String accountStatus,
            Wallet wallet,
            TeacherProfile teacher,
            StudentProfile student
    ) {
    }
}
