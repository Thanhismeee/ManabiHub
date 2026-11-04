package com.manabihub.refund.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.PageResponse;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.order.entity.OrderItem;
import com.manabihub.order.entity.OrderItemSnapshot;
import com.manabihub.order.repository.OrderItemSnapshotRepository;
import com.manabihub.payment.entity.PaymentTransaction;
import com.manabihub.payment.enums.PaymentStatus;
import com.manabihub.payment.repository.PaymentTransactionRepository;
import com.manabihub.refund.dto.request.RefundDecisionRequest;
import com.manabihub.refund.dto.request.RefundQueueFilterRequest;
import com.manabihub.refund.dto.response.RefundDetailResponse;
import com.manabihub.refund.dto.response.RefundQueueResponse;
import com.manabihub.refund.entity.RefundProviderAttempt;
import com.manabihub.refund.entity.RefundRequest;
import com.manabihub.refund.enums.RefundStatus;
import com.manabihub.refund.mapper.RefundMapper;
import com.manabihub.refund.repository.RefundProviderAttemptRepository;
import com.manabihub.refund.repository.RefundRequestRepository;
import com.manabihub.refund.repository.RefundRequestSpecifications;
import com.manabihub.refund.service.RefundService;
import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "decidedAt", "status", "id");

    private final RefundRequestRepository refundRequestRepository;
    private final RefundMapper refundMapper;
    private final CurrentUserService currentUserService;
    private final RefundDecisionTransactionService decisionTransactionService;
    private final OrderItemSnapshotRepository snapshotRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EscrowLedgerRepository escrowLedgerRepository;
    private final RefundProviderAttemptRepository attemptRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundQueueResponse> searchRefunds(
            RefundQueueFilterRequest filter,
            Pageable pageable
    ) {
        decisionTransactionService.requireAccess(
                currentUserService.getCurrentUserId()
        );
        validateFilter(filter);
        Page<RefundRequest> page = refundRequestRepository.findAll(
                RefundRequestSpecifications.from(filter),
                safePageable(pageable)
        );
        return PageResponse.from(page.map(refund -> enrichQueueResponse(
                refundMapper.toQueueResponse(refund),
                refund
        )));
    }

    private void validateFilter(RefundQueueFilterRequest filter) {
        if (filter == null) {
            return;
        }
        if (filter.getMinAmount() != null && filter.getMinAmount().signum() < 0
                || filter.getMaxAmount() != null && filter.getMaxAmount().signum() < 0) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Refund amount filters cannot be negative");
        }
        if (filter.getMinAmount() != null && filter.getMaxAmount() != null
                && filter.getMinAmount().compareTo(filter.getMaxAmount()) > 0) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Minimum amount cannot exceed maximum amount");
        }
        if (filter.getCreatedFrom() != null && filter.getCreatedTo() != null
                && !filter.getCreatedFrom().isBefore(filter.getCreatedTo())) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Created range must satisfy from < to");
        }
        if (filter.getDecidedFrom() != null && filter.getDecidedTo() != null
                && !filter.getDecidedFrom().isBefore(filter.getDecidedTo())) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Decision range must satisfy from < to");
        }
    }

    private Pageable safePageable(Pageable requested) {
        int page = Math.max(0, requested == null ? 0 : requested.getPageNumber());
        int size = Math.min(100, Math.max(1, requested == null ? 20 : requested.getPageSize()));
        List<Sort.Order> orders = requested == null ? List.of() : requested.getSort().stream()
                .filter(order -> ALLOWED_SORTS.contains(order.getProperty()))
                .toList();
        Sort sort = orders.isEmpty()
                ? Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                : Sort.by(orders).and(Sort.by(Sort.Order.desc("id")));
        return PageRequest.of(page, size, sort);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundDetailResponse getRefundDetail(UUID refundId) {
        decisionTransactionService.requireAccess(
                currentUserService.getCurrentUserId()
        );
        RefundRequest request = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "Refund request not found", HttpStatus.NOT_FOUND));
        return enrichDetailResponse(refundMapper.toDetailResponse(request), request);
    }

    @Override
    public void approveRefund(UUID refundId, RefundDecisionRequest request) {
        RefundStatus outcome = decisionTransactionService.approveToStudentWallet(
                refundId,
                request,
                currentUserService.getCurrentUserId()
        );
        if (outcome == RefundStatus.RECONCILIATION_REQUIRED) {
            throw reconciliationRequired();
        }
    }

    @Override
    public void rejectRefund(UUID refundId, RefundDecisionRequest request) {
        decisionTransactionService.reject(
                refundId,
                request,
                currentUserService.getCurrentUserId()
        );
    }

    private BusinessException reconciliationRequired() {
        return new BusinessException(
                MessageCodes.REFUND_RECONCILIATION_REQUIRED,
                "Refund was not finalized; Finance reconciliation is required",
                HttpStatus.CONFLICT
        );
    }

    private RefundQueueResponse enrichQueueResponse(
            RefundQueueResponse response,
            RefundRequest refund
    ) {
        OrderItem item = refund.getOrderItem();
        if (item != null) {
            response.setOrderItemId(item.getId());
            response.setCourseId(item.getCourse().getId());
            response.setCourseTitle(item.getCourse().getTitle());
            snapshotRepository.findByOrderItem_Id(item.getId()).ifPresent(snapshot -> {
                response.setCurrency(snapshot.getCurrency());
                response.setGrossAmount(snapshot.getGrossAmount());
            });
        }
        response.setProviderStatus(refund.getProviderStatus().name());
        response.setReconciliationReasonCode(
                refund.getReconciliationReasonCode()
        );
        paymentTransactionRepository
                .findByOrder_IdOrderByCreatedAtDesc(refund.getOrder().getId())
                .stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS
                        || payment.getStatus() == PaymentStatus.REFUNDED)
                .findFirst()
                .ifPresent(payment -> {
                    response.setPaymentStatus(payment.getStatus().name());
                    response.setPaymentProvider(payment.getProvider());
                    response.setPaymentAmount(payment.getAmount());
                });
        return response;
    }

    private RefundDetailResponse enrichDetailResponse(
            RefundDetailResponse response,
            RefundRequest refund
    ) {
        OrderItem item = refund.getOrderItem();
        if (item != null) {
            response.setOrderItemId(item.getId());
            response.setCourseId(item.getCourse().getId());
            response.setCourseTitle(item.getCourse().getTitle());
            snapshotRepository.findByOrderItem_Id(item.getId())
                    .ifPresent(snapshot -> setSnapshotEvidence(response, snapshot));
            escrowLedgerRepository.findByOrderItem_Id(item.getId())
                    .ifPresent(escrow -> setEscrowEvidence(response, escrow));
        }

        paymentTransactionRepository
                .findByOrder_IdOrderByCreatedAtDesc(refund.getOrder().getId())
                .stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS
                        || payment.getStatus() == PaymentStatus.REFUNDED)
                .findFirst()
                .ifPresent(payment -> setPaymentEvidence(response, payment));

        response.setProviderStatus(refund.getProviderStatus().name());
        response.setProviderAttemptCount(refund.getProviderAttemptCount());
        response.setReconciliationReasonCode(
                refund.getReconciliationReasonCode()
        );
        response.setDecisionReasonCode(
                refund.getDecisionReasonCode() == null
                        ? null
                        : refund.getDecisionReasonCode().name()
        );
        attemptRepository.findByRefundRequest_Id(refund.getId())
                .ifPresent(attempt -> setProviderEvidence(response, attempt));
        return response;
    }

    private void setSnapshotEvidence(
            RefundDetailResponse response,
            OrderItemSnapshot snapshot
    ) {
        response.setCurrency(snapshot.getCurrency());
        response.setGrossAmount(snapshot.getGrossAmount());
        response.setCommissionAmount(snapshot.getCommissionAmount());
        response.setTeacherNetAmount(snapshot.getTeacherNetAmount());
    }

    private void setPaymentEvidence(
            RefundDetailResponse response,
            PaymentTransaction payment
    ) {
        response.setPaymentStatus(payment.getStatus().name());
        response.setPaymentProvider(payment.getProvider());
        response.setPaymentProviderTransactionId(
                payment.getProviderTransactionId()
        );
        response.setPaymentAmount(payment.getAmount());
    }

    private void setEscrowEvidence(
            RefundDetailResponse response,
            EscrowLedger escrow
    ) {
        response.setEscrowStatus(escrow.getStatus().name());
        response.setEscrowAmount(escrow.getAmount());
        response.setEscrowReleaseAt(escrow.getReleaseAt());
    }

    private void setProviderEvidence(
            RefundDetailResponse response,
            RefundProviderAttempt attempt
    ) {
        response.setProviderName(attempt.getProvider());
        response.setProviderReference(attempt.getProviderReference());
        response.setProviderResultCode(attempt.getResultCode());
        response.setProviderAttemptCount(attempt.getAttemptCount());
    }
}
