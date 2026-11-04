package com.manabihub.wallet.service.impl;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.order.entity.Order;
import com.manabihub.order.entity.OrderItem;
import com.manabihub.order.entity.OrderItemSnapshot;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.repository.OrderItemRepository;
import com.manabihub.order.repository.OrderItemSnapshotRepository;
import com.manabihub.order.repository.OrderRepository;
import com.manabihub.refund.service.RefundWindow;
import com.manabihub.systemconfig.model.CommercialPolicy;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.entity.PlatformCommissionLedger;
import com.manabihub.wallet.enums.EscrowStatus;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.repository.PlatformCommissionLedgerRepository;
import com.manabihub.wallet.service.EscrowService;
import com.manabihub.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowServiceImpl implements EscrowService {

    private final EscrowLedgerRepository escrowLedgerRepository;
    private final OrderItemRepository orderItemRepository;
    private final WalletService walletService;
    private final AuditLogRepository auditLogRepository;
    private final OrderItemSnapshotRepository orderItemSnapshotRepository;
    private final PlatformCommissionLedgerRepository platformCommissionLedgerRepository;
    private final CommercialPolicyService commercialPolicyService;
    private final com.manabihub.kyc.repository.TeacherProfileRepository teacherProfileRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.manabihub.wallet.dto.response.EscrowLedgerResponse> getTeacherEscrowLedgerByUserId(UUID userId, org.springframework.data.domain.Pageable pageable) {
        TeacherProfile teacherProfile = teacherProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.KYC_TEACHER_NOT_FOUND,
                        "Teacher profile not found",
                        HttpStatus.NOT_FOUND));
        
        return escrowLedgerRepository.findByTeacher_Id(teacherProfile.getId(), pageable)
                .map(ledger -> {
                    OrderItemSnapshot snapshot = orderItemSnapshotRepository.findByOrderItem_Id(ledger.getOrderItem().getId())
                            .orElseThrow(() -> new BusinessException(MessageCodes.ORDER_NOT_FOUND, "Snapshot not found"));
                    return com.manabihub.wallet.dto.response.EscrowLedgerResponse.builder()
                            .id(ledger.getId())
                            .orderId(ledger.getOrder().getId())
                            .courseName(ledger.getCourse().getTitle())
                            .grossAmount(snapshot.getGrossAmount())
                            .platformCommissionAmount(snapshot.getCommissionAmount())
                            .teacherNetAmount(snapshot.getTeacherNetAmount())
                            .status(ledger.getStatus())
                            .releaseAt(ledger.getReleaseAt())
                            .createdAt(ledger.getCreatedAt())
                            .build();
                });
    }

    @Override
    @Transactional
    public List<EscrowLedger> holdForOrder(Order order) {
        requirePaidOrder(order);

        List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
        validateOrderItems(order, items);

        List<EscrowLedger> existing = escrowLedgerRepository.findByOrder_Id(order.getId());
        if (!existing.isEmpty()) {
            validateExistingAllocation(items, existing);
            return existing;
        }

        CommercialPolicy policy = commercialPolicyService.getCurrentPolicy();
        validateSettlementPolicy(order, items, policy);
        Instant allocatedAt = Instant.now();
        Instant holdingDeadline = allocatedAt.plus(Duration.ofDays(policy.escrowHoldingDays()));
        Instant refundDeadline = RefundWindow.exclusiveDeadline(allocatedAt, policy.refundWindowDays());
        Instant releaseAt = holdingDeadline.isAfter(refundDeadline) ? holdingDeadline : refundDeadline;
        List<EscrowLedger> created = new ArrayList<>();

        for (OrderItem item : items) {
            Course course = item.getCourse();
            TeacherProfile teacher = course.getTeacher();
            BigDecimal grossAmount = settlementMoney(item.getPrice(), policy.currency());
            BigDecimal platformCommission = grossAmount
                    .multiply(policy.commissionRate())
                    .setScale(0, RoundingMode.HALF_UP)
                    .setScale(2);
            BigDecimal teacherNet = grossAmount.subtract(platformCommission);

            orderItemSnapshotRepository.save(OrderItemSnapshot.builder()
                    .orderItem(item)
                    .currency(order.getCurrency())
                    .grossAmount(grossAmount)
                    .commissionRate(policy.commissionRate())
                    .commissionAmount(platformCommission)
                    .teacherNetAmount(teacherNet)
                    .commercialPolicyVersion(policy.policyVersion())
                    .escrowDays(policy.escrowHoldingDays())
                    .refundWindowDays(policy.refundWindowDays())
                    .refundProgressLimitPercent(policy.refundProgressLimitPercent())
                    .build());

            EscrowLedger escrow = escrowLedgerRepository.save(EscrowLedger.builder()
                    .order(order)
                    .orderItem(item)
                    .course(course)
                    .teacher(teacher)
                    .amount(teacherNet)
                    .status(EscrowStatus.HELD)
                    .releaseAt(releaseAt)
                    .build());

            appendCommissionEvent(
                    order,
                    item,
                    platformCommission,
                    PlatformCommissionLedger.CommissionEventType.COMMISSION_HELD);

            if (teacherNet.signum() > 0) {
                walletService.holdEscrow(
                        teacher,
                        teacherNet,
                        "ESCROW",
                        escrow.getId(),
                        "Teacher net held for paid order " + order.getOrderCode());
            }

            created.add(escrow);
        }

        return created;
    }

    @Override
    @Transactional

    public boolean processEscrowRelease(UUID escrowId) {
        // Serialize release with refund submission/settlement using the same order lock.
        UUID orderId = escrowLedgerRepository.findOrderIdById(escrowId)
                .orElseThrow(() -> integrityViolation("Escrow record was not found"));
        orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> integrityViolation("Escrow order was not found"));
        EscrowLedger escrow = escrowLedgerRepository.findByIdForUpdate(escrowId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.WALLET_NOT_FOUND,
                        "Escrow record was not found",
                        HttpStatus.NOT_FOUND));

        if (escrow.getStatus() != EscrowStatus.HELD) {
            log.info("Escrow {} is not HELD, skipping release", escrowId);
            return false;
        }

        if (escrow.getReleaseAt().isAfter(Instant.now())) {
            log.info("Escrow {} clearing time has not been reached, skipping release", escrowId);
            return false;
        }

        // Also protect old HELD rows whose release_at predates this fix, and
        // policy extensions. Missing payment evidence must never release funds.
        Instant paymentCutoff = Instant.now().atZone(RefundWindow.BUSINESS_ZONE).toLocalDate()
                .minusDays(commercialPolicyService.getCurrentPolicy().refundWindowDays())
                .atStartOfDay(RefundWindow.BUSINESS_ZONE).toInstant();
        if (escrowLedgerRepository.isRefundWindowOpen(orderId, paymentCutoff)) {
            return false;
        }

        if (escrow.getTeacher().getUser().getUserStatus() != UserStatus.ACTIVE) {
            log.info("Escrow {} blocked due to inactive teacher account", escrowId);
            return false;
        }

        if (escrow.getOrder().getStatus() != OrderStatus.PAID) {
            log.info("Escrow {} blocked because order status is {}", escrowId, escrow.getOrder().getStatus());
            return false;
        }

        if (escrowLedgerRepository.existsBlockingRefundRequest(escrow.getOrder().getId())) {
            log.info("Escrow {} blocked by an active refund request", escrowId);
            return false;
        }

        if (escrowLedgerRepository.existsPendingTrustCase(
                escrow.getCourse().getId(),
                escrow.getTeacher().getUser().getId())) {
            log.info("Escrow {} blocked by an open trust case", escrowId);
            return false;
        }

        OrderItem item = requireOrderItem(escrow);
        OrderItemSnapshot snapshot = requireSnapshot(item);
        ensureCommissionEventAbsent(
                item,
                PlatformCommissionLedger.CommissionEventType.COMMISSION_RECOGNIZED);

        if (escrow.getAmount().signum() > 0) {
            walletService.releaseEscrow(
                    escrow.getTeacher(),
                    escrow.getAmount(),
                    "ESCROW",
                    escrow.getId(),
                    "Escrow released to teacher available balance");
        }

        appendCommissionEvent(
                escrow.getOrder(),
                item,
                snapshot.getCommissionAmount(),
                PlatformCommissionLedger.CommissionEventType.COMMISSION_RECOGNIZED);

        escrow.setStatus(EscrowStatus.RELEASED);
        escrowLedgerRepository.save(escrow);
        auditLogRepository.save(audit(
                "ESCROW_RELEASE",
                escrow,
                "release_at reached with no refund, trust case, or account block"));

        return true;
    }

    @Override
    @Transactional
    public boolean reverseHeldAllocationForRefund(UUID orderItemId) {
        EscrowLedger escrow = escrowLedgerRepository.findByOrderItemIdForUpdate(orderItemId)
                .orElseThrow(() -> integrityViolation(
                        "Paid order item has no escrow allocation"));
        return reverseHeldEscrow(escrow);
    }

    @Override
    @Transactional
    public boolean reverseHeldAllocationsForRefund(UUID orderId) {
        List<EscrowLedger> escrows = escrowLedgerRepository.findByOrderIdForUpdate(orderId);
        if (escrows.isEmpty()) {
            throw integrityViolation("Paid order has no escrow allocations");
        }

        boolean releasedAllocationExists = escrows.stream()
                .anyMatch(escrow -> escrow.getStatus() == EscrowStatus.RELEASED);
        if (releasedAllocationExists) {
            throw new BusinessException(
                    MessageCodes.REFUND_RECONCILIATION_REQUIRED,
                    "Refund cannot be auto-posted after teacher funds were released",
                    HttpStatus.CONFLICT);
        }

        boolean reversed = false;
        for (EscrowLedger escrow : escrows) {
            reversed = reverseHeldEscrow(escrow) || reversed;
        }

        return reversed;
    }

    private boolean reverseHeldEscrow(EscrowLedger escrow) {
        if (escrow.getStatus() == EscrowStatus.REFUNDED) {
            return false;
        }
        if (escrow.getStatus() == EscrowStatus.RELEASED) {
            throw new BusinessException(
                    MessageCodes.REFUND_RECONCILIATION_REQUIRED,
                    "Refund cannot be auto-posted after teacher funds were released",
                    HttpStatus.CONFLICT);
        }
        if (escrow.getStatus() != EscrowStatus.HELD
                && escrow.getStatus() != EscrowStatus.FROZEN) {
            throw integrityViolation("Unsupported escrow state during refund reversal");
        }

        OrderItem item = requireOrderItem(escrow);
        OrderItemSnapshot snapshot = requireSnapshot(item);
        if (escrow.getAmount().compareTo(snapshot.getTeacherNetAmount()) != 0) {
            throw integrityViolation(
                    "Escrow amount does not match the immutable teacher-net snapshot");
        }
        ensureCommissionEventAbsent(
                item,
                PlatformCommissionLedger.CommissionEventType.COMMISSION_REVERSED);

        if (escrow.getAmount().signum() > 0) {
            walletService.refundHeldEscrow(
                    escrow.getTeacher(),
                    escrow.getAmount(),
                    "ESCROW",
                    escrow.getId(),
                    "Held teacher allocation reversed after confirmed refund");
        }

        appendCommissionEvent(
                escrow.getOrder(),
                item,
                snapshot.getCommissionAmount(),
                PlatformCommissionLedger.CommissionEventType.COMMISSION_REVERSED);

        escrow.setStatus(EscrowStatus.REFUNDED);
        escrowLedgerRepository.save(escrow);
        auditLogRepository.save(audit(
                "ESCROW_REFUND_REVERSE",
                escrow,
                "provider-confirmed refund reversed this order item before release"));
        return true;
    }

    private void requirePaidOrder(Order order) {
        if (order == null || order.getId() == null || order.getStatus() != OrderStatus.PAID) {
            throw integrityViolation("Escrow allocation requires a persisted PAID order");
        }
        if (order.getCurrency() == null || order.getCurrency().isBlank()) {
            throw integrityViolation("Paid order currency is missing");
        }
    }

    private void validateOrderItems(Order order, List<OrderItem> items) {
        if (items.isEmpty()) {
            throw integrityViolation("Paid order has no order items");
        }
        BigDecimal itemTotal = items.stream()
                .map(OrderItem::getPrice)
                .map(this::money)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        if (order.getTotalAmount() == null
                || itemTotal.compareTo(money(order.getTotalAmount())) != 0) {
            throw integrityViolation("Paid order total does not equal its item total");
        }
    }

    private void validateExistingAllocation(List<OrderItem> items, List<EscrowLedger> existing) {
        if (existing.size() != items.size()) {
            throw integrityViolation("Existing escrow allocation is incomplete");
        }

        Set<UUID> expectedItemIds = new HashSet<>();
        for (OrderItem item : items) {
            expectedItemIds.add(item.getId());
            if (!orderItemSnapshotRepository.existsByOrderItem_Id(item.getId())
                    || !platformCommissionLedgerRepository.existsByOrderItem_IdAndEventType(
                    item.getId(),
                    PlatformCommissionLedger.CommissionEventType.COMMISSION_HELD)) {
                throw integrityViolation("Existing escrow is missing immutable financial history");
            }
        }

        Set<UUID> allocatedItemIds = new HashSet<>();
        for (EscrowLedger escrow : existing) {
            if (escrow.getOrderItem() == null) {
                throw integrityViolation("Existing escrow is not linked to an order item");
            }
            allocatedItemIds.add(escrow.getOrderItem().getId());
        }
        if (!expectedItemIds.equals(allocatedItemIds)) {
            throw integrityViolation("Existing escrow does not match the paid order items");
        }
    }

    private void validateSettlementPolicy(
            Order order,
            List<OrderItem> items,
            CommercialPolicy policy
    ) {
        if (!order.getCurrency().equals(policy.currency())) {
            throw integrityViolation(
                    "Paid order currency does not match the active commercial policy");
        }

        settlementMoney(order.getTotalAmount(), policy.currency());
        items.forEach(item -> settlementMoney(item.getPrice(), policy.currency()));
    }

    private OrderItem requireOrderItem(EscrowLedger escrow) {
        if (escrow.getOrderItem() == null) {
            throw integrityViolation("Escrow is not linked to an order item");
        }
        return escrow.getOrderItem();
    }

    private OrderItemSnapshot requireSnapshot(OrderItem item) {
        return orderItemSnapshotRepository.findByOrderItem_Id(item.getId())
                .orElseThrow(() -> integrityViolation(
                        "Order item is missing its immutable commercial snapshot"));
    }

    private void ensureCommissionEventAbsent(
            OrderItem item,
            PlatformCommissionLedger.CommissionEventType eventType
    ) {
        if (platformCommissionLedgerRepository.existsByOrderItem_IdAndEventType(
                item.getId(),
                eventType)) {
            throw integrityViolation("Commission event already exists before escrow state transition");
        }
    }

    private void appendCommissionEvent(
            Order order,
            OrderItem item,
            BigDecimal amount,
            PlatformCommissionLedger.CommissionEventType eventType
    ) {
        platformCommissionLedgerRepository.save(PlatformCommissionLedger.builder()
                .order(order)
                .orderItem(item)
                .amount(money(amount))
                .eventType(eventType)
                .build());
    }

    private BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw integrityViolation("Financial amount must not be negative");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw integrityViolation("Financial amount has more than two decimal places");
        }
    }

    private BigDecimal settlementMoney(BigDecimal amount, String currency) {
        BigDecimal normalized = money(amount);
        if (!"VND".equals(currency)) {
            return normalized;
        }
        try {
            return normalized.setScale(0, RoundingMode.UNNECESSARY).setScale(2);
        } catch (ArithmeticException exception) {
            throw integrityViolation("VND settlement amount must be a whole number");
        }
    }

    private AuditLog audit(String action, EscrowLedger escrow, String reason) {
        return AuditLog.builder()
                .actorType("SYSTEM")
                .action(action)
                .targetType("ESCROW_LEDGER")
                .targetId(escrow.getId())
                .metadata(Map.of(
                        "orderId", escrow.getOrder().getId().toString(),
                        "decision", "APPROVED",
                        "reason", reason))
                .build();
    }

    private BusinessException integrityViolation(String message) {
        return new BusinessException(
                MessageCodes.FINANCIAL_INTEGRITY_VIOLATION,
                message,
                HttpStatus.CONFLICT);

    }
}
