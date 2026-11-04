package com.manabihub.wallet.service.impl;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.order.entity.Order;
import com.manabihub.order.entity.OrderItem;
import com.manabihub.order.entity.OrderItemSnapshot;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.repository.OrderItemRepository;
import com.manabihub.order.repository.OrderItemSnapshotRepository;
import com.manabihub.systemconfig.model.CommercialPolicy;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.entity.PlatformCommissionLedger;
import com.manabihub.wallet.enums.EscrowStatus;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.repository.PlatformCommissionLedgerRepository;
import com.manabihub.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscrowServiceImplTest {

    @Mock private EscrowLedgerRepository escrowLedgerRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private WalletService walletService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private OrderItemSnapshotRepository orderItemSnapshotRepository;
    @Mock private PlatformCommissionLedgerRepository platformCommissionLedgerRepository;
    @Mock private CommercialPolicyService commercialPolicyService;
    @Mock private com.manabihub.order.repository.OrderRepository orderRepository;

    @InjectMocks
    private EscrowServiceImpl service;

    private Order order;
    private Course course;
    private TeacherProfile teacher;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUserStatus(UserStatus.ACTIVE);
        teacher = new TeacherProfile();
        teacher.setId(UUID.randomUUID());
        teacher.setUser(user);
        course = Course.builder()
                .id(UUID.randomUUID())
                .title("N3 Grammar")
                .teacher(teacher)
                .build();
        order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("OD1")
                .totalAmount(new BigDecimal("100000.00"))
                .currency("VND")
                .status(OrderStatus.PAID)
                .build();
    }

    @Test
    void holdForOrder_100000VndSnapshots20000CommissionAnd80000TeacherNet() {
        OrderItem item = configureNewAllocation(new BigDecimal("100000.00"));

        service.holdForOrder(order);

        OrderItemSnapshot snapshot = captureSnapshot();
        assertEquals(new BigDecimal("100000.00"), snapshot.getGrossAmount());
        assertEquals(new BigDecimal("0.20"), snapshot.getCommissionRate());
        assertEquals(new BigDecimal("20000.00"), snapshot.getCommissionAmount());
        assertEquals(new BigDecimal("80000.00"), snapshot.getTeacherNetAmount());
        assertEquals("VND", snapshot.getCurrency());
        assertEquals("policy-2026-07-28", snapshot.getCommercialPolicyVersion());
        assertEquals(14, snapshot.getEscrowDays());

        EscrowLedger escrow = captureEscrow();
        assertEquals(item, escrow.getOrderItem());
        assertEquals(new BigDecimal("80000.00"), escrow.getAmount());

        PlatformCommissionLedger heldEvent = captureCommissionEvent();
        assertEquals(
                PlatformCommissionLedger.CommissionEventType.COMMISSION_HELD,
                heldEvent.getEventType());
        assertEquals(new BigDecimal("20000.00"), heldEvent.getAmount());

        verify(walletService).holdEscrow(
                teacher,
                new BigDecimal("80000.00"),
                "ESCROW",
                escrow.getId(),
                "Teacher net held for paid order OD1");
    }

    @Test
    void holdForOrder_roundsCommissionToWholeVndUsingHalfUp() {
        order.setTotalAmount(new BigDecimal("100003.00"));
        configureNewAllocation(new BigDecimal("100003.00"));

        service.holdForOrder(order);

        OrderItemSnapshot snapshot = captureSnapshot();
        assertEquals(new BigDecimal("20001.00"), snapshot.getCommissionAmount());
        assertEquals(new BigDecimal("80002.00"), snapshot.getTeacherNetAmount());
    }

    @Test
    void holdForOrder_existingCompleteAllocationIsIdempotent() {
        OrderItem item = item(new BigDecimal("100000.00"));
        EscrowLedger existing = EscrowLedger.builder()
                .id(UUID.randomUUID())
                .order(order)
                .orderItem(item)
                .course(course)
                .teacher(teacher)
                .amount(new BigDecimal("80000.00"))
                .status(EscrowStatus.HELD)
                .build();
        when(orderItemRepository.findByOrder_Id(order.getId())).thenReturn(List.of(item));
        when(escrowLedgerRepository.findByOrder_Id(order.getId())).thenReturn(List.of(existing));
        when(orderItemSnapshotRepository.existsByOrderItem_Id(item.getId())).thenReturn(true);
        when(platformCommissionLedgerRepository.existsByOrderItem_IdAndEventType(
                item.getId(),
                PlatformCommissionLedger.CommissionEventType.COMMISSION_HELD))
                .thenReturn(true);

        assertEquals(List.of(existing), service.holdForOrder(order));

        verify(escrowLedgerRepository, never()).save(any());
        verifyNoInteractions(walletService, commercialPolicyService);
    }

    @Test
    void processEscrowRelease_usesHistoricalSnapshotAndAppendsRecognition() {
        OrderItem item = item(new BigDecimal("100000.00"));
        EscrowLedger escrow = eligibleEscrow(item);
        OrderItemSnapshot snapshot = snapshot(item, new BigDecimal("0.20"),
                new BigDecimal("20000.00"), new BigDecimal("80000.00"));
        configureEligibleRelease(escrow, item, snapshot);

        boolean released = service.processEscrowRelease(escrow.getId());

        assertTrue(released);
        assertEquals(EscrowStatus.RELEASED, escrow.getStatus());
        verify(walletService).releaseEscrow(
                teacher,
                new BigDecimal("80000.00"),
                "ESCROW",
                escrow.getId(),
                "Escrow released to teacher available balance");
        PlatformCommissionLedger recognized = captureCommissionEvent();
        assertEquals(
                PlatformCommissionLedger.CommissionEventType.COMMISSION_RECOGNIZED,
                recognized.getEventType());
        assertEquals(new BigDecimal("20000.00"), recognized.getAmount());
        verify(commercialPolicyService).getCurrentPolicy();
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void processEscrowRelease_whenRefundIsPendingKeepsFundsHeld() {
        OrderItem item = item(new BigDecimal("100000.00"));
        EscrowLedger escrow = eligibleEscrow(item);
        configureReleaseGuard(escrow);
        when(escrowLedgerRepository.findByIdForUpdate(escrow.getId())).thenReturn(Optional.of(escrow));
        when(escrowLedgerRepository.existsBlockingRefundRequest(order.getId())).thenReturn(true);

        assertFalse(service.processEscrowRelease(escrow.getId()));

        assertEquals(EscrowStatus.HELD, escrow.getStatus());
        verifyNoInteractions(walletService, orderItemSnapshotRepository);
    }

    @Test
    void reverseHeldAllocationsForRefund_reversesTeacherAndCommissionExactlyOnce() {
        OrderItem item = item(new BigDecimal("100000.00"));
        EscrowLedger escrow = eligibleEscrow(item);
        OrderItemSnapshot snapshot = snapshot(item, new BigDecimal("0.20"),
                new BigDecimal("20000.00"), new BigDecimal("80000.00"));
        when(escrowLedgerRepository.findByOrderIdForUpdate(order.getId()))
                .thenReturn(List.of(escrow));
        when(orderItemSnapshotRepository.findByOrderItem_Id(item.getId()))
                .thenReturn(Optional.of(snapshot));

        assertTrue(service.reverseHeldAllocationsForRefund(order.getId()));
        assertFalse(service.reverseHeldAllocationsForRefund(order.getId()));

        assertEquals(EscrowStatus.REFUNDED, escrow.getStatus());
        verify(walletService, times(1)).refundHeldEscrow(
                teacher,
                new BigDecimal("80000.00"),
                "ESCROW",
                escrow.getId(),
                "Held teacher allocation reversed after confirmed refund");
        PlatformCommissionLedger reversed = captureCommissionEvent();
        assertEquals(
                PlatformCommissionLedger.CommissionEventType.COMMISSION_REVERSED,
                reversed.getEventType());
        assertEquals(new BigDecimal("20000.00"), reversed.getAmount());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void reverseHeldAllocationsForRefund_afterReleaseRequiresManualReconciliation() {
        OrderItem item = item(new BigDecimal("100000.00"));
        EscrowLedger escrow = eligibleEscrow(item);
        escrow.setStatus(EscrowStatus.RELEASED);
        when(escrowLedgerRepository.findByOrderIdForUpdate(order.getId()))
                .thenReturn(List.of(escrow));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.reverseHeldAllocationsForRefund(order.getId()));

        assertEquals(MessageCodes.REFUND_RECONCILIATION_REQUIRED, exception.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        verifyNoInteractions(walletService, orderItemSnapshotRepository);
    }

    @Test
    void holdForOrder_whenOrderTotalDoesNotMatchItemsFailsClosed() {
        OrderItem item = item(new BigDecimal("99999.00"));
        when(orderItemRepository.findByOrder_Id(order.getId())).thenReturn(List.of(item));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.holdForOrder(order));

        assertEquals(MessageCodes.FINANCIAL_INTEGRITY_VIOLATION, exception.getMessageCode());
        verifyNoInteractions(commercialPolicyService, walletService);
    }

    @Test
    void holdForOrder_whenOrderCurrencyDoesNotMatchPolicyFailsClosed() {
        order.setCurrency("USD");
        configureAllocationInput(new BigDecimal("100000.00"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.holdForOrder(order));

        assertEquals(MessageCodes.FINANCIAL_INTEGRITY_VIOLATION, exception.getMessageCode());
        verify(orderItemSnapshotRepository, never()).save(any());
        verifyNoInteractions(walletService);
    }

    @Test
    void holdForOrder_whenVndAmountIsFractionalFailsClosed() {
        order.setTotalAmount(new BigDecimal("100000.50"));
        configureAllocationInput(new BigDecimal("100000.50"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.holdForOrder(order));

        assertEquals(MessageCodes.FINANCIAL_INTEGRITY_VIOLATION, exception.getMessageCode());
        verify(orderItemSnapshotRepository, never()).save(any());
        verifyNoInteractions(walletService);
    }

    private OrderItem configureNewAllocation(BigDecimal price) {
        OrderItem item = configureAllocationInput(price);
        when(escrowLedgerRepository.save(any(EscrowLedger.class))).thenAnswer(invocation -> {
            EscrowLedger escrow = invocation.getArgument(0);
            if (escrow.getId() == null) {
                escrow.setId(UUID.randomUUID());
            }
            return escrow;
        });
        return item;
    }

    private OrderItem configureAllocationInput(BigDecimal price) {
        OrderItem item = item(price);
        when(orderItemRepository.findByOrder_Id(order.getId())).thenReturn(List.of(item));
        when(escrowLedgerRepository.findByOrder_Id(order.getId())).thenReturn(List.of());
        when(commercialPolicyService.getCurrentPolicy()).thenReturn(policy());
        return item;
    }

    private void configureEligibleRelease(
            EscrowLedger escrow,
            OrderItem item,
            OrderItemSnapshot snapshot
    ) {
        configureReleaseGuard(escrow);
        when(escrowLedgerRepository.findByIdForUpdate(escrow.getId()))
                .thenReturn(Optional.of(escrow));
        when(escrowLedgerRepository.existsBlockingRefundRequest(order.getId())).thenReturn(false);
        when(escrowLedgerRepository.existsPendingTrustCase(course.getId(), teacher.getUser().getId()))
                .thenReturn(false);
        when(orderItemSnapshotRepository.findByOrderItem_Id(item.getId()))
                .thenReturn(Optional.of(snapshot));
    }

    private void configureReleaseGuard(EscrowLedger escrow) {
        when(escrowLedgerRepository.findOrderIdById(escrow.getId())).thenReturn(Optional.of(order.getId()));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(commercialPolicyService.getCurrentPolicy()).thenReturn(policy());
    }

    @Test
    void processEscrowRelease_oldReleaseDateStillInsideRefundWindow_keepsFundsHeld() {
        EscrowLedger escrow = eligibleEscrow(item(new BigDecimal("100000.00")));
        configureReleaseGuard(escrow);
        when(escrowLedgerRepository.findByIdForUpdate(escrow.getId())).thenReturn(Optional.of(escrow));
        when(escrowLedgerRepository.isRefundWindowOpen(eq(order.getId()), any())).thenReturn(true);

        assertFalse(service.processEscrowRelease(escrow.getId()));

        assertEquals(EscrowStatus.HELD, escrow.getStatus());
        verifyNoInteractions(walletService, orderItemSnapshotRepository);
    }

    @Test
    void holdForOrder_shortEscrowPolicyStillProtectsEntireRefundWindow() {
        configureNewAllocation(new BigDecimal("100000.00"));
        when(commercialPolicyService.getCurrentPolicy()).thenReturn(new CommercialPolicy(
                "VND", new BigDecimal("0.20"), 14, 20, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, 1, 2, "short-hold", Instant.now()));
        Instant before = Instant.now();

        service.holdForOrder(order);

        assertFalse(captureEscrow().getReleaseAt().isBefore(
                com.manabihub.refund.service.RefundWindow.exclusiveDeadline(before, 14)));
    }

    private CommercialPolicy policy() {
        return new CommercialPolicy(
                "VND",
                new BigDecimal("0.20"),
                7,
                30,
                14,
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                1,
                2,
                "policy-2026-07-28",
                Instant.parse("2026-07-28T00:00:00Z"));
    }

    private OrderItem item(BigDecimal price) {
        return OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .course(course)
                .price(price)
                .build();
    }

    private EscrowLedger eligibleEscrow(OrderItem item) {
        return EscrowLedger.builder()
                .id(UUID.randomUUID())
                .order(order)
                .orderItem(item)
                .course(course)
                .teacher(teacher)
                .amount(new BigDecimal("80000.00"))
                .status(EscrowStatus.HELD)
                .releaseAt(Instant.now().minusSeconds(60))
                .createdAt(Instant.now().minusSeconds(120))
                .build();
    }

    private OrderItemSnapshot snapshot(
            OrderItem item,
            BigDecimal rate,
            BigDecimal commission,
            BigDecimal teacherNet
    ) {
        return OrderItemSnapshot.builder()
                .orderItem(item)
                .currency("VND")
                .grossAmount(item.getPrice())
                .commissionRate(rate)
                .commissionAmount(commission)
                .teacherNetAmount(teacherNet)
                .commercialPolicyVersion("historical-policy")
                .escrowDays(14)
                .build();
    }

    private OrderItemSnapshot captureSnapshot() {
        ArgumentCaptor<OrderItemSnapshot> captor = ArgumentCaptor.forClass(OrderItemSnapshot.class);
        verify(orderItemSnapshotRepository).save(captor.capture());
        return captor.getValue();
    }

    private EscrowLedger captureEscrow() {
        ArgumentCaptor<EscrowLedger> captor = ArgumentCaptor.forClass(EscrowLedger.class);
        verify(escrowLedgerRepository).save(captor.capture());
        return captor.getValue();
    }

    private PlatformCommissionLedger captureCommissionEvent() {
        ArgumentCaptor<PlatformCommissionLedger> captor =
                ArgumentCaptor.forClass(PlatformCommissionLedger.class);
        verify(platformCommissionLedgerRepository).save(captor.capture());
        return captor.getValue();
    }
}
