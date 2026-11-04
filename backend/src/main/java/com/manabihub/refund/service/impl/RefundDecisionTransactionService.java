package com.manabihub.refund.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.order.entity.Order;
import com.manabihub.order.entity.OrderItem;
import com.manabihub.order.entity.OrderItemSnapshot;
import com.manabihub.order.enums.OrderItemRefundStatus;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.repository.OrderItemRepository;
import com.manabihub.order.repository.OrderItemSnapshotRepository;
import com.manabihub.order.repository.OrderRepository;
import com.manabihub.payment.entity.PaymentTransaction;
import com.manabihub.payment.enums.PaymentStatus;
import com.manabihub.payment.repository.PaymentTransactionRepository;
import com.manabihub.refund.dto.request.RefundDecisionRequest;
import com.manabihub.refund.entity.RefundProviderAttempt;
import com.manabihub.refund.entity.RefundRequest;
import com.manabihub.refund.enums.EligibilityResult;
import com.manabihub.refund.enums.RefundDecisionReason;
import com.manabihub.refund.enums.RefundProviderStatus;
import com.manabihub.refund.enums.RefundStatus;
import com.manabihub.refund.enums.RefundSettlementMethod;
import com.manabihub.refund.enums.RefundSettlementStatus;
import com.manabihub.refund.enums.StudentRefundType;
import com.manabihub.refund.gateway.RefundGatewayCommand;
import com.manabihub.refund.gateway.RefundGatewayResult;
import com.manabihub.refund.repository.RefundProviderAttemptRepository;
import com.manabihub.refund.repository.RefundRequestRepository;
import com.manabihub.refund.service.RefundAfterCommitNotifier;
import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.enums.EscrowStatus;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.service.EscrowService;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.service.StudentWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundDecisionTransactionService {

    private static final int MAX_PROVIDER_TEXT = 500;
    private static final String PERMISSION_REFUND_REVIEW = "REFUND_REVIEW";

    private final RefundRequestRepository refundRequestRepository;
    private final RefundProviderAttemptRepository attemptRepository;
    private final InternalAdminAccountRepository adminAccountRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemSnapshotRepository snapshotRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final com.manabihub.learning.service.EnrollmentAccessService enrollmentAccessService;
    private final EscrowLedgerRepository escrowLedgerRepository;
    private final EscrowService escrowService;
    private final AuditLogService auditLogService;
    private final RefundAfterCommitNotifier afterCommitNotifier;
    private final StudentWalletService studentWalletService;

    @Transactional
    public RefundStatus approveToStudentWallet(
            UUID refundId,
            RefundDecisionRequest decision,
            UUID adminId
    ) {
        requireApprovalReason(decision.getReasonCode());
        InternalAdminAccount admin = requireAdmin(adminId);
        RefundRequest refund = lockRefund(refundId);
        if (refund.getStatus() == RefundStatus.APPROVED) {
            return RefundStatus.APPROVED;
        }
        requireApprovableState(refund);

        refund.setDecidedBy(admin);
        refund.setDecisionNote(normalizeDecisionNote(decision.getNote()));
        refund.setDecisionReasonCode(decision.getReasonCode());
        refund.setDecidedAt(Instant.now());
        refund.setSettlementMethod(RefundSettlementMethod.WALLET);
        refund.setSettlementStatus(RefundSettlementStatus.PENDING);

        return settleToStudentWallet(refund, admin, false);
    }

    /**
     * Executes BR-REF-01 without a Finance actor. Eligibility evidence is
     * persisted before this method is called; settlement still uses the same
     * locked, idempotent wallet-refund path as a manual Finance approval.
     */
    @Transactional(timeout = 30)
    public RefundRequest autoApproveToStudentWallet(UUID refundId) {
        RefundRequest refund = lockRefund(refundId);
        if (refund.getStatus() == RefundStatus.APPROVED) {
            return refund;
        }
        requireApprovableState(refund);
        if (refund.getEligibilitySnapshot() == null
                || refund.getEligibilitySnapshot().getEligibilityResult()
                != EligibilityResult.STANDARD_ELIGIBLE
                || refund.getEligibilitySnapshot().getRefundType() != StudentRefundType.STANDARD
                || !Boolean.TRUE.equals(refund.getEligibilitySnapshot().getEligible())) {
            throw conflict("Refund request does not contain auto-approval eligibility evidence");
        }

        refund.setDecidedBy(null);
        refund.setDecisionNote("Tự động phê duyệt theo BR-REF-01");
        refund.setDecisionReasonCode(RefundDecisionReason.STANDARD_ELIGIBLE);
        refund.setDecidedAt(Instant.now());
        refund.setSettlementMethod(RefundSettlementMethod.WALLET);
        refund.setSettlementStatus(RefundSettlementStatus.PENDING);

        settleToStudentWallet(refund, null, true);
        return refund;
    }

    private RefundStatus settleToStudentWallet(
            RefundRequest refund,
            InternalAdminAccount admin,
            boolean automatic
    ) {

        ValidationContext context = validateBeforeProvider(
                refund,
                refund.getDecisionReasonCode()
        );
        if (context.failureReason() != null) {
            refund.setSettlementStatus(RefundSettlementStatus.FAILED);
            moveToReconciliation(
                    refund,
                    RefundProviderStatus.NOT_REQUESTED,
                    context.failureReason()
            );
            if (automatic) {
                auditAutomaticRefund(
                        refund,
                        "AUTO_REFUND_RECONCILIATION_REQUIRED",
                        "RECONCILIATION_REQUIRED",
                        Map.of("reason", context.failureReason())
                );
            } else {
                auditReconciliation(admin, refund, context.failureReason());
            }
            return RefundStatus.RECONCILIATION_REQUIRED;
        }

        boolean reversed = escrowService.reverseHeldAllocationForRefund(
                context.orderItem().getId()
        );
        if (!reversed) {
            throw conflict("Order item allocation was already reversed");
        }

        WalletTransaction walletCredit = studentWalletService.creditRefund(
                refund.getStudent().getId(),
                context.snapshot().getGrossAmount(),
                refund.getId(),
                "Hoàn tiền khóa học vào ví cho yêu cầu " + refund.getId()
        );

        enrollmentAccessService.updatePurchaseStatus(refund.getStudent().getId(), context.orderItem().getCourse().getId(),
                context.orderItem().getId(), EnrollmentStatus.REFUNDED);

        OrderItem item = context.orderItem();
        item.setRefundStatus(OrderItemRefundStatus.REFUNDED);
        item.setRefundedAt(Instant.now());
        orderItemRepository.save(item);

        if (allOrderItemsRefunded(context.order(), item.getId())) {
            context.order().setStatus(OrderStatus.REFUNDED);
            orderRepository.save(context.order());
        }

        refund.setStatus(RefundStatus.APPROVED);
        refund.setProviderStatus(RefundProviderStatus.NOT_REQUESTED);
        refund.setProviderAttemptCount(0);
        refund.setReconciliationReasonCode(null);
        refund.setSettlementStatus(RefundSettlementStatus.COMPLETED);
        refund.setSettledAt(Instant.now());
        refund.setWalletTransactionId(walletCredit.getId());
        refund.setAutoRefundNextAttemptAt(null);
        refund.setAutoRefundLastErrorCode(null);
        refundRequestRepository.save(refund);

        Map<String, Object> approvalEvidence = Map.of(
                "status", "APPROVED",
                "reasonCode", refund.getDecisionReasonCode().name(),
                "orderItemId", item.getId().toString(),
                "amount", context.snapshot().getGrossAmount().toPlainString(),
                "settlementMethod", RefundSettlementMethod.WALLET.name(),
                "walletTransactionId", walletCredit.getId().toString()
        );
        if (automatic) {
            auditAutomaticRefund(
                    refund,
                    "AUTO_APPROVE_REFUND_TO_WALLET",
                    "APPROVED",
                    approvalEvidence
            );
        } else {
            auditLogService.logAdminAction(
                    admin.getId(),
                    adminRoleCode(admin),
                    "APPROVE_REFUND_TO_WALLET",
                    "REFUND_REQUEST",
                    refund.getId(),
                    Map.of("status", "PENDING"),
                    approvalEvidence,
                    Map.of("providerStatus", RefundProviderStatus.NOT_REQUESTED.name())
            );
        }
        scheduleNotifications(refund, RefundStatus.APPROVED);
        return RefundStatus.APPROVED;
    }

    private void auditAutomaticRefund(
            RefundRequest refund,
            String action,
            String status,
            Map<String, Object> evidence
    ) {
        auditLogService.logUserAction(
                refund.getStudent().getUser().getId(),
                "STUDENT",
                action,
                "REFUND_REQUEST",
                refund.getId(),
                Map.of("status", "PENDING"),
                evidence,
                Map.of(
                        "workflow", "BR-REF-01",
                        "result", status,
                        "providerStatus", RefundProviderStatus.NOT_REQUESTED.name()
                )
        );
    }

    @Transactional(readOnly = true)
    public void requireAccess(UUID adminId) {
        requireAdmin(adminId);
    }

    @Transactional
    public PreparedApproval prepareApproval(
            UUID refundId,
            RefundDecisionRequest decision,
            UUID adminId,
            String refundProvider
    ) {
        requireApprovalReason(decision.getReasonCode());
        InternalAdminAccount admin = requireAdmin(adminId);
        RefundRequest refund = lockRefund(refundId);

        if (refund.getStatus() == RefundStatus.APPROVED) {
            return PreparedApproval.completed();
        }
        requireApprovableState(refund);

        refund.setDecidedBy(admin);
        refund.setDecisionNote(normalizeDecisionNote(decision.getNote()));
        refund.setDecisionReasonCode(decision.getReasonCode());
        refund.setDecidedAt(Instant.now());

        ValidationContext context = validateBeforeProvider(refund, decision.getReasonCode());
        if (context.failureReason() != null) {
            moveToReconciliation(
                    refund,
                    RefundProviderStatus.NOT_REQUESTED,
                    context.failureReason()
            );
            auditReconciliation(admin, refund, context.failureReason());
            return PreparedApproval.reconciliation();
        }

        String idempotencyKey = "refund:" + refund.getId();
        String providerRequestId = "refund-request:" + refund.getId();
        RefundProviderAttempt attempt = attemptRepository
                .findByRefundRequest_Id(refund.getId())
                .orElseGet(() -> RefundProviderAttempt.builder()
                        .refundRequest(refund)
                        .idempotencyKey(idempotencyKey)
                        .providerRequestId(providerRequestId)
                        .attemptCount(0)
                        .build());

        attempt.setProvider(normalizeProvider(refundProvider));
        attempt.setRequestedAmount(context.snapshot().getGrossAmount());
        attempt.setCurrency(context.snapshot().getCurrency());
        attempt.setStatus(RefundProviderStatus.PROCESSING);
        attempt.setAttemptCount(attempt.getAttemptCount() + 1);
        attempt.setResultAuthenticated(false);
        attempt.setResultCode(null);
        attempt.setResultMessage(null);
        attempt.setCompletedAt(null);
        attemptRepository.save(attempt);

        refund.setStatus(RefundStatus.PROCESSING);
        refund.setProviderStatus(RefundProviderStatus.PROCESSING);
        refund.setProviderAttemptCount(attempt.getAttemptCount());
        refund.setReconciliationReasonCode(null);
        refundRequestRepository.save(refund);

        PaymentTransaction payment = context.payment();
        Order order = context.order();
        OrderItem item = context.orderItem();
        return PreparedApproval.execute(new RefundGatewayCommand(
                refund.getId(),
                order.getId(),
                item.getId(),
                order.getOrderCode(),
                payment.getProvider(),
                payment.getProviderTransactionId(),
                providerRequestId,
                idempotencyKey,
                context.snapshot().getGrossAmount(),
                context.snapshot().getCurrency()
        ));
    }

    @Transactional
    public RefundStatus completeApproval(
            PreparedApproval prepared,
            RefundGatewayResult providerResult
    ) {
        providerResult = normalizeProviderResult(providerResult);
        RefundGatewayCommand command = Objects.requireNonNull(prepared.command());
        RefundRequest refund = lockRefund(command.refundRequestId());
        if (refund.getStatus() == RefundStatus.APPROVED) {
            return RefundStatus.APPROVED;
        }
        if (refund.getStatus() != RefundStatus.PROCESSING) {
            throw conflict("Refund request is not waiting for a provider result");
        }

        RefundProviderAttempt attempt = requireAttempt(command.idempotencyKey());
        recordProviderResult(attempt, providerResult);
        refund.setProviderStatus(providerResult.status());
        refund.setProviderAttemptCount(attempt.getAttemptCount());

        String providerFailure = providerFailureReason(command, providerResult);
        if (providerFailure != null) {
            moveToReconciliation(refund, providerResult.status(), providerFailure);
            attemptRepository.save(attempt);
            auditReconciliation(
                    requireAdmin(refund.getDecidedBy().getId()),
                    refund,
                    providerFailure
            );
            return RefundStatus.RECONCILIATION_REQUIRED;
        }

        ValidationContext context = validateBeforeProvider(
                refund,
                refund.getDecisionReasonCode()
        );
        if (context.failureReason() != null) {
            moveToReconciliation(
                    refund,
                    RefundProviderStatus.SUCCESS,
                    "POST_PROVIDER_" + context.failureReason()
            );
            attemptRepository.save(attempt);
            auditReconciliation(
                    requireAdmin(refund.getDecidedBy().getId()),
                    refund,
                    refund.getReconciliationReasonCode()
            );
            return RefundStatus.RECONCILIATION_REQUIRED;
        }

        boolean reversed = escrowService.reverseHeldAllocationForRefund(
                context.orderItem().getId()
        );
        if (!reversed) {
            throw conflict("Order item allocation was already reversed");
        }

        enrollmentAccessService.updatePurchaseStatus(refund.getStudent().getId(), context.orderItem().getCourse().getId(),
                context.orderItem().getId(), EnrollmentStatus.REFUNDED);

        OrderItem item = context.orderItem();
        item.setRefundStatus(OrderItemRefundStatus.REFUNDED);
        item.setRefundedAt(Instant.now());
        orderItemRepository.save(item);

        if (allOrderItemsRefunded(context.order(), item.getId())) {
            context.order().setStatus(OrderStatus.REFUNDED);
            orderRepository.save(context.order());
        }

        refund.setStatus(RefundStatus.APPROVED);
        refund.setProviderStatus(RefundProviderStatus.SUCCESS);
        refund.setReconciliationReasonCode(null);
        refundRequestRepository.save(refund);
        attemptRepository.save(attempt);

        InternalAdminAccount admin = requireAdmin(refund.getDecidedBy().getId());
        auditLogService.logAdminAction(
                admin.getId(),
                adminRoleCode(admin),
                "APPROVE_REFUND",
                "REFUND_REQUEST",
                refund.getId(),
                Map.of("status", "PROCESSING"),
                Map.of(
                        "status", "APPROVED",
                        "reasonCode", refund.getDecisionReasonCode().name(),
                        "orderItemId", item.getId().toString(),
                        "amount", context.snapshot().getGrossAmount().toPlainString(),
                        "providerReference", safe(providerResult.providerReference())
                ),
                Map.of("providerStatus", "SUCCESS")
        );
        scheduleNotifications(refund, RefundStatus.APPROVED);
        return RefundStatus.APPROVED;
    }

    @Transactional
    public void recordFinalizationFailure(
            PreparedApproval prepared,
            RefundGatewayResult providerResult
    ) {
        providerResult = normalizeProviderResult(providerResult);
        RefundGatewayCommand command = Objects.requireNonNull(prepared.command());
        RefundRequest refund = lockRefund(command.refundRequestId());
        if (refund.getStatus() == RefundStatus.APPROVED) {
            return;
        }
        RefundProviderAttempt attempt = requireAttempt(command.idempotencyKey());
        recordProviderResult(attempt, providerResult);
        attemptRepository.save(attempt);
        moveToReconciliation(
                refund,
                providerResult.status(),
                "ACCOUNTING_FINALIZATION_FAILED"
        );
        auditReconciliation(
                requireAdmin(refund.getDecidedBy().getId()),
                refund,
                "ACCOUNTING_FINALIZATION_FAILED"
        );
    }

    @Transactional
    public void reject(
            UUID refundId,
            RefundDecisionRequest decision,
            UUID adminId
    ) {
        requireRejectionReason(decision.getReasonCode());
        InternalAdminAccount admin = requireAdmin(adminId);
        RefundRequest refund = lockRefund(refundId);
        if (refund.getStatus() == RefundStatus.REJECTED) {
            return;
        }
        if (refund.getStatus() != RefundStatus.PENDING
                && refund.getStatus() != RefundStatus.RECONCILIATION_REQUIRED) {
            throw conflict("Refund request cannot be rejected from its current state");
        }
        if (refund.getProviderStatus() == RefundProviderStatus.SUCCESS) {
            throw conflict(
                    "Provider already confirmed the refund; accounting reconciliation is required");
        }

        RefundStatus statusBefore = refund.getStatus();
        refund.setStatus(RefundStatus.REJECTED);
        refund.setAutoRefundNextAttemptAt(null);
        refund.setDecisionReasonCode(decision.getReasonCode());
        refund.setDecisionNote(normalizeDecisionNote(decision.getNote()));
        refund.setDecidedBy(admin);
        refund.setDecidedAt(Instant.now());
        reopenEnrollmentIfPending(refund);
        refundRequestRepository.save(refund);

        auditLogService.logAdminAction(
                admin.getId(),
                adminRoleCode(admin),
                "REJECT_REFUND",
                "REFUND_REQUEST",
                refund.getId(),
                Map.of("status", statusBefore.name()),
                Map.of(
                        "status", "REJECTED",
                        "reasonCode", decision.getReasonCode().name()
                ),
                Map.of()
        );
        scheduleNotifications(refund, RefundStatus.REJECTED);
    }

    private void reopenEnrollmentIfPending(RefundRequest refund) {
        if (refund.getStudent() == null || refund.getOrderItem() == null
                || refund.getOrderItem().getCourse() == null) {
            return;
        }
        enrollmentAccessService.updatePurchaseStatus(refund.getStudent().getId(), refund.getOrderItem().getCourse().getId(),
                refund.getOrderItem().getId(), EnrollmentStatus.ACTIVE);
    }

    private ValidationContext validateBeforeProvider(
            RefundRequest refund,
            RefundDecisionReason decisionReason
    ) {
        OrderItem item = refund.getOrderItem();
        if (item == null) {
            return ValidationContext.failed("MISSING_OR_AMBIGUOUS_ORDER_ITEM");
        }
        Order order = orderRepository.findByIdForUpdate(refund.getOrder().getId())
                .orElse(null);
        if (order == null || !item.getOrder().getId().equals(order.getId())) {
            return ValidationContext.failed("ORDER_ITEM_RELATIONSHIP_MISMATCH");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            return ValidationContext.failed("ORDER_NOT_PAID");
        }
        if (!order.getStudent().getId().equals(refund.getStudent().getId())) {
            return ValidationContext.failed("STUDENT_ORDER_MISMATCH");
        }
        if (item.getRefundStatus() == OrderItemRefundStatus.REFUNDED) {
            return ValidationContext.failed("ORDER_ITEM_ALREADY_REFUNDED");
        }

        OrderItemSnapshot snapshot = snapshotRepository
                .findByOrderItem_Id(item.getId())
                .orElse(null);
        if (snapshot == null
                || snapshot.getGrossAmount().compareTo(item.getPrice()) != 0
                || !snapshot.getCurrency().equals(order.getCurrency())
                || snapshot.getGrossAmount().compareTo(
                snapshot.getTeacherNetAmount().add(snapshot.getCommissionAmount())) != 0) {
            return ValidationContext.failed("IMMUTABLE_FINANCIAL_SNAPSHOT_INVALID");
        }
        if (snapshot.getGrossAmount().signum() <= 0) {
            return ValidationContext.failed("REFUND_AMOUNT_NOT_POSITIVE");
        }

        List<PaymentTransaction> paymentComposition = paymentTransactionRepository
                .findByOrder_IdAndStatusInOrderByCreatedAtAsc(
                        order.getId(),
                        List.of(PaymentStatus.SUCCESS)
                );
        BigDecimal capturedTotal = paymentComposition.stream()
                .map(PaymentTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean invalidComponent = paymentComposition.isEmpty()
                || paymentComposition.stream().anyMatch(payment ->
                        payment.getProviderTransactionId() == null
                                || payment.getProviderTransactionId().isBlank()
                                || payment.getSucceededAt() == null
                                || payment.getAmount() == null
                                || payment.getAmount().signum() <= 0);
        if (invalidComponent || capturedTotal.compareTo(order.getTotalAmount()) != 0) {
            return ValidationContext.failed("CONFIRMED_PAYMENT_EVIDENCE_INVALID");
        }
        PaymentTransaction payment = paymentComposition.stream()
                .filter(component -> !"WALLET".equals(component.getProvider()))
                .findFirst()
                .orElse(paymentComposition.get(0));

        EscrowLedger escrow = escrowLedgerRepository
                .findByOrderItemIdForUpdate(item.getId())
                .orElse(null);
        if (escrow == null) {
            return ValidationContext.failed("ESCROW_ALLOCATION_MISSING");
        }
        if (escrow.getStatus() == EscrowStatus.RELEASED) {
            return ValidationContext.failed("TEACHER_FUNDS_ALREADY_RELEASED");
        }
        if (escrow.getStatus() != EscrowStatus.HELD
                && escrow.getStatus() != EscrowStatus.FROZEN) {
            return ValidationContext.failed("ESCROW_STATE_NOT_REVERSIBLE");
        }
        if (escrow.getAmount().compareTo(snapshot.getTeacherNetAmount()) != 0) {
            return ValidationContext.failed("ESCROW_SNAPSHOT_AMOUNT_MISMATCH");
        }

        String eligibilityFailure = validateEligibilitySnapshot(
                refund,
                item,
                snapshot,
                decisionReason
        );
        if (eligibilityFailure != null) {
            return ValidationContext.failed(eligibilityFailure);
        }
        return new ValidationContext(order, item, snapshot, payment, null);
    }

    private String validateEligibilitySnapshot(
            RefundRequest refund,
            OrderItem item,
            OrderItemSnapshot financialSnapshot,
            RefundDecisionReason decisionReason
    ) {
        com.manabihub.refund.dto.RefundEligibilitySnapshot snapshot = refund.getEligibilitySnapshot();
        if (snapshot == null) {
            return "ELIGIBILITY_SNAPSHOT_MISSING";
        }
        if (!java.util.Objects.equals(snapshot.getOrderId(), refund.getOrder().getId())
                || !java.util.Objects.equals(snapshot.getOrderItemId(), item.getId())) {
            return "ELIGIBILITY_REFERENCE_MISMATCH";
        }
        BigDecimal paidAmount = snapshot.getActuallyPaidAmount();
        if (paidAmount == null
                || paidAmount.compareTo(financialSnapshot.getGrossAmount()) != 0) {
            return "ELIGIBILITY_PAID_AMOUNT_MISMATCH";
        }
        if (snapshot.getPolicyVersion() == null || snapshot.getPolicyVersion().isBlank()
                || snapshot.getPaymentSucceededAt() == null
                || snapshot.getRequestedAt() == null
                || snapshot.getMeasuredProgressPercent() == null) {
            return "ELIGIBILITY_EVIDENCE_INCOMPLETE";
        }

        boolean standardEligible = (snapshot.getEligible() != null && snapshot.getEligible())
                || "ELIGIBLE".equals(snapshot.getResult())
                || "STANDARD_ELIGIBLE".equals(snapshot.getResult())
                || snapshot.getEligibilityResult() == com.manabihub.refund.enums.EligibilityResult.STANDARD_ELIGIBLE;
        
        if (decisionReason == RefundDecisionReason.STANDARD_ELIGIBLE) {
            return standardEligible ? null : "STANDARD_ELIGIBILITY_NOT_CONFIRMED";
        }

        boolean manualReview = (snapshot.getEligible() != null && !snapshot.getEligible())
                || "MANUAL_REVIEW".equals(snapshot.getResult())
                || "EXCEPTION_REVIEW".equals(snapshot.getResult())
                || snapshot.getEligibilityResult() == com.manabihub.refund.enums.EligibilityResult.MANUAL_REVIEW_REQUIRED;
                
        return decisionReason.isManualException() && manualReview
                ? null
                : "MANUAL_EXCEPTION_NOT_DOCUMENTED";
    }

    private boolean allOrderItemsRefunded(Order order, UUID justRefundedItemId) {
        List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
        return !items.isEmpty() && items.stream().allMatch(item ->
                item.getId().equals(justRefundedItemId)
                        || item.getRefundStatus() == OrderItemRefundStatus.REFUNDED);
    }

    private String providerFailureReason(
            RefundGatewayCommand command,
            RefundGatewayResult result
    ) {
        if (result == null) {
            return "PROVIDER_RESULT_MISSING";
        }
        if (result.status() != RefundProviderStatus.SUCCESS) {
            return "PROVIDER_" + result.status().name();
        }
        if (!result.authenticated()) {
            return "PROVIDER_RESULT_NOT_AUTHENTICATED";
        }
        if (result.providerReference() == null
                || result.providerReference().isBlank()) {
            return "PROVIDER_REFERENCE_MISSING";
        }
        if (result.refundedAmount() == null
                || result.refundedAmount().compareTo(command.amount()) != 0) {
            return "PROVIDER_REFUND_AMOUNT_MISMATCH";
        }
        return null;
    }

    private void recordProviderResult(
            RefundProviderAttempt attempt,
            RefundGatewayResult result
    ) {
        RefundGatewayResult safeResult = normalizeProviderResult(result);
        attempt.setStatus(safeResult.status());
        attempt.setProviderReference(safeNullable(
                safeResult.providerReference(),
                255
        ));
        attempt.setResultCode(safeNullable(safeResult.resultCode(), 100));
        attempt.setResultMessage(safeNullable(
                safeResult.resultMessage(),
                MAX_PROVIDER_TEXT
        ));
        attempt.setResultAuthenticated(safeResult.authenticated());
        attempt.setCompletedAt(Instant.now());
    }

    private void moveToReconciliation(
            RefundRequest refund,
            RefundProviderStatus providerStatus,
            String reason
    ) {
        refund.setStatus(RefundStatus.RECONCILIATION_REQUIRED);
        refund.setProviderStatus(providerStatus);
        refund.setReconciliationReasonCode(reason);
        refund.setAutoRefundNextAttemptAt(null);
        refundRequestRepository.save(refund);
    }

    private void auditReconciliation(
            InternalAdminAccount admin,
            RefundRequest refund,
            String reason
    ) {
        auditLogService.logAdminAction(
                admin.getId(),
                adminRoleCode(admin),
                "REFUND_RECONCILIATION_REQUIRED",
                "REFUND_REQUEST",
                refund.getId(),
                Map.of(),
                Map.of(
                        "status", "RECONCILIATION_REQUIRED",
                        "reasonCode", reason
                ),
                Map.of("providerStatus", refund.getProviderStatus().name())
        );
    }

    private void scheduleNotifications(RefundRequest refund, RefundStatus outcome) {
        OrderItem item = refund.getOrderItem();
        if (item == null) {
            return;
        }

        RefundAfterCommitNotifier.Recipient studentRecipient = null;
        if (refund.getStudent() != null && refund.getStudent().getUser() != null) {
            var u = refund.getStudent().getUser();
            studentRecipient = new RefundAfterCommitNotifier.Recipient(u.getId(), u.getEmail());
        }

        RefundAfterCommitNotifier.Recipient teacherRecipient = null;
        if (item.getCourse() != null && item.getCourse().getTeacher() != null && item.getCourse().getTeacher().getUser() != null) {
            var u = item.getCourse().getTeacher().getUser();
            teacherRecipient = new RefundAfterCommitNotifier.Recipient(u.getId(), u.getEmail());
        }

        String orderCode = refund.getOrder() != null ? refund.getOrder().getOrderCode() : "N/A";
        String courseTitle = item.getCourse() != null ? item.getCourse().getTitle() : "N/A";

        afterCommitNotifier.schedule(
                refund.getId(),
                outcome,
                studentRecipient,
                teacherRecipient,
                orderCode,
                courseTitle
        );
    }

    private RefundRequest lockRefund(UUID refundId) {
        return refundRequestRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Refund request not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private RefundProviderAttempt requireAttempt(String idempotencyKey) {
        return attemptRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> conflict("Refund provider attempt was not found"));
    }

    private InternalAdminAccount requireAdmin(UUID adminId) {
        InternalAdminAccount admin = adminAccountRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Admin not found",
                        HttpStatus.UNAUTHORIZED
                ));
        if (!adminAccountRepository.hasPermission(
                adminId,
                PERMISSION_REFUND_REVIEW
        )) {
            throw new BusinessException(
                    MessageCodes.ADMIN_PERMISSION_DENIED,
                    "A live refund-review permission is required",
                    HttpStatus.FORBIDDEN
            );
        }
        return admin;
    }

    /**
     * Legacy admin rows can exist before a role assignment was backfilled.  The
     * role is audit metadata, not a prerequisite for the already-authorized
     * refund settlement, so a missing mapping must not turn a successful wallet
     * refund into a 500/rollback.
     */
    private String adminRoleCode(InternalAdminAccount admin) {
        if (admin == null || admin.getRole() == null || admin.getRole().getCode() == null) {
            return "INTERNAL_ADMIN";
        }
        return admin.getRole().getCode().name();
    }

    private String normalizeDecisionNote(String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "A decision note is required",
                    HttpStatus.BAD_REQUEST
            );
        }
        return note.trim();
    }

    private void requireApprovableState(RefundRequest refund) {
        if (refund.getStatus() == RefundStatus.PROCESSING) {
            throw conflict(
                    "A refund provider call is already processing; retry is not allowed");
        }
        if (refund.getStatus() != RefundStatus.PENDING
                && refund.getStatus() != RefundStatus.RECONCILIATION_REQUIRED) {
            throw conflict("Refund request cannot be approved from its current state");
        }
        if (refund.getProviderStatus() == RefundProviderStatus.SUCCESS
                && refund.getStatus() == RefundStatus.RECONCILIATION_REQUIRED) {
            throw conflict(
                    "Provider already confirmed the refund; accounting reconciliation is required");
        }
    }

    private void requireApprovalReason(RefundDecisionReason reason) {
        if (reason == null || !reason.isApprovalReason()) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "A valid approval reason code is required",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void requireRejectionReason(RefundDecisionReason reason) {
        if (reason == null || !reason.isRejectionReason()) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "A valid rejection reason code is required",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private BusinessException conflict(String message) {
        return new BusinessException(
                MessageCodes.REFUND_RECONCILIATION_REQUIRED,
                message,
                HttpStatus.CONFLICT
        );
    }

    private boolean matchesUuid(Object value, UUID expected) {
        if (value instanceof UUID uuid) {
            return uuid.equals(expected);
        }
        try {
            return expected.equals(UUID.fromString(string(value)));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return value == null ? null : new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool
                ? bool
                : "true".equalsIgnoreCase(string(value));
    }

    private boolean blank(Object value) {
        return value == null || value.toString().isBlank();
    }

    private String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String safe(String value) {
        String safe = safeNullable(value, MAX_PROVIDER_TEXT);
        return safe == null ? "" : safe;
    }

    private String safeNullable(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private String normalizeProvider(String provider) {
        String normalized = safeNullable(provider, 50);
        return normalized == null || normalized.isBlank()
                ? "UNAVAILABLE"
                : normalized;
    }

    private RefundGatewayResult normalizeProviderResult(
            RefundGatewayResult result
    ) {
        if (result == null) {
            return new RefundGatewayResult(
                    RefundProviderStatus.INVALID_RESULT,
                    false,
                    null,
                    "PROVIDER_RESULT_MISSING",
                    "Refund provider returned no result",
                    null
            );
        }
        if (result.status() == null
                || result.status() == RefundProviderStatus.NOT_REQUESTED
                || result.status() == RefundProviderStatus.PROCESSING) {
            return new RefundGatewayResult(
                    RefundProviderStatus.INVALID_RESULT,
                    false,
                    safeNullable(result.providerReference(), 255),
                    "PROVIDER_RESULT_INVALID_STATUS",
                    "Refund provider returned a non-terminal status",
                    result.refundedAmount()
            );
        }
        return result;
    }

    public record PreparedApproval(
            boolean gatewayRequired,
            boolean reconciliationRequired,
            RefundGatewayCommand command
    ) {
        static PreparedApproval execute(RefundGatewayCommand command) {
            return new PreparedApproval(true, false, command);
        }

        static PreparedApproval completed() {
            return new PreparedApproval(false, false, null);
        }

        static PreparedApproval reconciliation() {
            return new PreparedApproval(false, true, null);
        }
    }

    private record ValidationContext(
            Order order,
            OrderItem orderItem,
            OrderItemSnapshot snapshot,
            PaymentTransaction payment,
            String failureReason
    ) {
        static ValidationContext failed(String reason) {
            return new ValidationContext(null, null, null, null, reason);
        }
    }
}
