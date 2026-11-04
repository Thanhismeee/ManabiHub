package com.manabihub.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.service.EnrollmentProgressResetService;
import com.manabihub.order.entity.Order;
import com.manabihub.order.entity.OrderItem;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.enums.OrderType;
import com.manabihub.order.repository.OrderItemRepository;
import com.manabihub.order.repository.OrderRepository;
import com.manabihub.payment.dto.IpnAckResponse;
import com.manabihub.payment.config.VnPayProperties;
import com.manabihub.payment.entity.PaymentTransaction;
import com.manabihub.payment.enums.PaymentStatus;
import com.manabihub.payment.event.PaymentNotificationEvent;
import com.manabihub.payment.gateway.PaymentCallbackResult;
import com.manabihub.payment.gateway.PaymentGateway;
import com.manabihub.payment.repository.PaymentTransactionRepository;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletPaymentReservation;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletReservationStatus;
import com.manabihub.wallet.service.EscrowService;
import com.manabihub.wallet.service.StudentWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentServiceImpl}.
 * <p>
 * Grouped with {@code @Nested} so Surefire reports one summary line per Report 5.1 sheet:
 * <pre>
 *   PaymentServiceImplTest$PayWithWallet -> sheet 40 payWithWallet
 *   PaymentServiceImplTest$HandleIpn     -> sheet 41 handleIpn
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentGateway paymentGateway;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentProgressResetService enrollmentProgressResetService;
    @Mock private com.manabihub.learning.service.EnrollmentAccessService enrollmentAccessService;
    @Mock private EscrowService escrowService;
    @Mock private StudentWalletService studentWalletService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentServiceImpl service;

    private Order order;
    private Course course;
    private final Map<String, String> params = Map.of("vnp_TxnRef", "OD1", "vnp_ResponseCode", "00");

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                paymentGateway, orderRepository, orderItemRepository, paymentTransactionRepository,
                enrollmentRepository, enrollmentProgressResetService, enrollmentAccessService, escrowService,
                studentWalletService, eventPublisher,
                new ObjectMapper(), new VnPayProperties());

        AppUser user = AppUser.builder().id(UUID.randomUUID()).email("student@test.dev").build();
        StudentProfile student = StudentProfile.builder().id(UUID.randomUUID()).user(user).build();
        course = Course.builder().id(UUID.randomUUID()).title("N3 Grammar").build();
        order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("OD1")
                .totalAmount(new BigDecimal("100.00"))
                .currency("VND")
                .status(OrderStatus.PENDING)
                .student(student)
                .build();

        lenient().when(paymentGateway.getProvider()).thenReturn("VNPAY");
    }

    private PaymentCallbackResult result(boolean validSig, boolean success, long amount) {
        return new PaymentCallbackResult(validSig, "OD1", "99999", amount,
                success ? "00" : "24", success ? "00" : "02", success);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 40 — payWithWallet (UC-08 Purchase Course) — 6 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 40 - payWithWallet (UC-08)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PayWithWallet {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - enough balance -> PAID + enrollment + escrow + notification")
        void payWithWallet_debitsWalletAndFulfilsCourseOrder() {
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(studentWalletService.reserveForOrder(any(), any(), any(), any()))
                    .thenReturn(WalletPaymentReservation.builder()
                            .id(UUID.randomUUID())
                            .orderId(order.getId())
                            .amount(order.getTotalAmount())
                            .status(WalletReservationStatus.RESERVED)
                            .build());
            when(studentWalletService.captureForOrder(eq(order.getId()), any(Instant.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());
            when(orderItemRepository.findByOrder_Id(order.getId()))
                    .thenReturn(List.of(OrderItem.builder().order(order).course(course).price(new BigDecimal("100.00")).build()));
            when(enrollmentRepository.findByStudentIdAndCourseIdForUpdate(any(), any())).thenReturn(Optional.empty());

            Order paidOrder = service.payWithWallet(order.getId());

            assertEquals(order, paidOrder);
            assertEquals(OrderStatus.PAID, order.getStatus());
            verify(studentWalletService).reserveForOrder(
                    eq(order.getStudent().getId()), eq(order.getId()),
                    eq(new BigDecimal("100.00")), any(Instant.class));
            verify(studentWalletService).captureForOrder(eq(order.getId()), any(Instant.class));
            ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
            verify(paymentTransactionRepository).save(txCaptor.capture());
            assertEquals("WALLET", txCaptor.getValue().getProvider());
            assertEquals(PaymentStatus.SUCCESS, txCaptor.getValue().getStatus());
            assertEquals(new BigDecimal("100.00"), txCaptor.getValue().getAmount());
            org.junit.jupiter.api.Assertions.assertNotNull(txCaptor.getValue().getSucceededAt());
            verify(enrollmentRepository).save(any(Enrollment.class));
            verify(escrowService).holdForOrder(order);
            ArgumentCaptor<PaymentNotificationEvent> eventCaptor =
                    ArgumentCaptor.forClass(PaymentNotificationEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertEquals(order.getId(), eventCaptor.getValue().orderId());
            assertEquals("student@test.dev", eventCaptor.getValue().recipientEmail());
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (N) - repurchase -> REFUNDED enrollment reset, not replaced")
        void payWithWallet_repurchaseResetsRefundedEnrollmentWithoutReplacingIt() {
            Instant protectedMaterialsDownloadedAt = Instant.parse("2026-07-20T00:00:00Z");
            Enrollment refundedEnrollment = Enrollment.builder()
                    .id(UUID.randomUUID())
                    .student(order.getStudent())
                    .course(course)
                    .status(EnrollmentStatus.REFUNDED)
                    .protectedMaterialsFullyDownloadedAt(protectedMaterialsDownloadedAt)
                    .build();
            UUID originalEnrollmentId = refundedEnrollment.getId();

            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(studentWalletService.reserveForOrder(any(), any(), any(), any()))
                    .thenReturn(WalletPaymentReservation.builder()
                            .id(UUID.randomUUID())
                            .orderId(order.getId())
                            .amount(order.getTotalAmount())
                            .status(WalletReservationStatus.RESERVED)
                            .build());
            when(studentWalletService.captureForOrder(eq(order.getId()), any(Instant.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());
            when(orderItemRepository.findByOrder_Id(order.getId()))
                    .thenReturn(List.of(OrderItem.builder()
                            .order(order)
                            .course(course)
                            .price(order.getTotalAmount())
                            .build()));
            when(enrollmentRepository.findByStudentIdAndCourseIdForUpdate(
                    order.getStudent().getId(), course.getId()))
                    .thenReturn(Optional.of(refundedEnrollment));

            service.payWithWallet(order.getId());

            assertEquals(originalEnrollmentId, refundedEnrollment.getId());
            verify(enrollmentProgressResetService).resetForRepurchase(refundedEnrollment);
            verify(escrowService).holdForOrder(order);
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (A) - order already PAID -> idempotent, no side effect")
        void payWithWallet_alreadyPaidOrder_isIdempotent() {
            order.setStatus(OrderStatus.PAID);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

            assertEquals(order, service.payWithWallet(order.getId()));

            verify(studentWalletService, never()).reserveForOrder(any(), any(), any(), any());
            verify(studentWalletService, never()).captureForOrder(any(), any());
            verify(paymentTransactionRepository, never()).save(any());
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
            verify(eventPublisher, never()).publishEvent(any(PaymentNotificationEvent.class));
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("UTCID04 (A) - unknown order id -> ORDER_NOT_FOUND")
        void payWithWallet_orderNotFound_throwsNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(orderRepository.findByIdForUpdate(unknownId)).thenReturn(Optional.empty());

            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.payWithWallet(unknownId));

            assertEquals(MessageCodes.ORDER_NOT_FOUND, error.getMessageCode());
            verify(studentWalletService, never()).reserveForOrder(any(), any(), any(), any());
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("UTCID05 (A) - order FAILED -> COMMON_CONFLICT")
        void payWithWallet_orderNoLongerPending_throwsConflict() {
            order.setStatus(OrderStatus.FAILED);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.payWithWallet(order.getId()));

            assertEquals(MessageCodes.COMMON_CONFLICT, error.getMessageCode());
            verify(studentWalletService, never()).reserveForOrder(any(), any(), any(), any());
            verify(escrowService, never()).holdForOrder(any());
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        @DisplayName("UTCID06 (A) - not enough balance -> WALLET_INSUFFICIENT_BALANCE")
        void payWithWallet_insufficientWalletBalance_doesNotFulfilTheOrder() {
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(studentWalletService.reserveForOrder(any(), any(), any(), any()))
                    .thenThrow(new BusinessException(
                            MessageCodes.WALLET_INSUFFICIENT_BALANCE,
                            "Số dư khả dụng của ví không đủ để thanh toán"));

            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.payWithWallet(order.getId()));

            assertEquals(MessageCodes.WALLET_INSUFFICIENT_BALANCE, error.getMessageCode());
            verify(studentWalletService, never()).captureForOrder(any(), any());
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 41 — handleIpn (UC-08 Purchase Course) — 10 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 41 - handleIpn (UC-08)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class HandleIpn {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - valid COURSE callback -> PAID + enrollment + escrow + notify")
        void handleIpn_validSuccessfulCallback_confirmsOrderAndCreatesEnrollmentEscrowNotification() {
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 10_000L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));
            when(paymentTransactionRepository.findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
                    order.getId(), "VNPAY"))
                    .thenReturn(Optional.of(PaymentTransaction.builder()
                            .order(order).provider("VNPAY").status(PaymentStatus.PENDING).build()));
            when(orderItemRepository.findByOrder_Id(order.getId()))
                    .thenReturn(List.of(OrderItem.builder().order(order).course(course).price(new BigDecimal("100.00")).build()));
            when(enrollmentRepository.findByStudentIdAndCourseIdForUpdate(any(), any())).thenReturn(Optional.empty());

            IpnAckResponse ack = service.handleIpn(params);

            assertEquals("00", ack.rspCode());
            assertEquals(OrderStatus.PAID, order.getStatus());

            ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
            verify(paymentTransactionRepository).save(txCaptor.capture());
            assertEquals(PaymentStatus.SUCCESS, txCaptor.getValue().getStatus());
            assertEquals("99999", txCaptor.getValue().getProviderTransactionId());

            verify(enrollmentRepository).save(any(Enrollment.class));
            verify(escrowService).holdForOrder(order);
            verify(eventPublisher).publishEvent(any(PaymentNotificationEvent.class));
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (N) - WALLET_TOPUP order -> wallet credited, no enrollment/escrow")
        void handleIpn_walletTopUp_creditsStudentWalletNotEnrollment() {
            Order topUp = Order.builder()
                    .id(UUID.randomUUID())
                    .orderCode("OD1")
                    .totalAmount(new BigDecimal("100.00"))
                    .currency("VND")
                    .status(OrderStatus.PENDING)
                    .student(order.getStudent())
                    .type(OrderType.WALLET_TOPUP)
                    .build();
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 10_000L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(topUp));
            when(paymentTransactionRepository.findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
                    topUp.getId(), "VNPAY"))
                    .thenReturn(Optional.of(PaymentTransaction.builder()
                            .order(topUp).provider("VNPAY").status(PaymentStatus.PENDING).build()));

            IpnAckResponse ack = service.handleIpn(params);

            assertEquals("00", ack.rspCode());
            assertEquals(OrderStatus.PAID, topUp.getStatus());
            verify(studentWalletService).creditTopUp(
                    eq(topUp.getStudent().getId()), eq(new BigDecimal("100.00")),
                    eq(topUp.getId()), anyString());
            // A top-up must never create enrollment or escrow.
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
            verify(eventPublisher).publishEvent(any(PaymentNotificationEvent.class));
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (N) - combined wallet + gateway -> wallet share captured")
        void handleIpn_combinedPayment_debitsWalletPortionAndConfirms() {
            order.setWalletAmount(new BigDecimal("30.00")); // 30 from wallet → gateway charges 70.00
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 7_000L)); // 70.00 × 100
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));
            PaymentTransaction gatewayPayment = PaymentTransaction.builder()
                    .order(order).provider("VNPAY").status(PaymentStatus.PENDING).build();
            PaymentTransaction walletPayment = PaymentTransaction.builder()
                    .order(order).provider("WALLET").amount(new BigDecimal("30.00"))
                    .status(PaymentStatus.PENDING).build();
            when(paymentTransactionRepository.findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
                    order.getId(), "VNPAY")).thenReturn(Optional.of(gatewayPayment));
            when(paymentTransactionRepository.findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
                    order.getId(), "WALLET")).thenReturn(Optional.of(walletPayment));
            when(studentWalletService.captureForOrder(eq(order.getId()), any(Instant.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());
            when(orderItemRepository.findByOrder_Id(order.getId()))
                    .thenReturn(List.of(OrderItem.builder().order(order).course(course).price(new BigDecimal("100.00")).build()));
            when(enrollmentRepository.findByStudentIdAndCourseIdForUpdate(any(), any())).thenReturn(Optional.empty());

            IpnAckResponse ack = service.handleIpn(params);

            assertEquals("00", ack.rspCode());
            verify(studentWalletService).captureForOrder(eq(order.getId()), any(Instant.class));
            assertEquals(PaymentStatus.SUCCESS, walletPayment.getStatus());
            assertEquals(new BigDecimal("30.00"), walletPayment.getAmount());
            org.junit.jupiter.api.Assertions.assertNotNull(walletPayment.getSucceededAt());
            verify(enrollmentRepository).save(any(Enrollment.class));
            verify(escrowService).holdForOrder(order);
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("UTCID04 (A) - invalid signature -> RspCode 97, nothing happens")
        void handleIpn_invalidSignature_returns97AndDoesNothing() {
            when(paymentGateway.parseCallback(params)).thenReturn(result(false, true, 10_000L));

            IpnAckResponse ack = service.handleIpn(params);

            assertEquals("97", ack.rspCode());
            verify(orderRepository, never()).findByOrderCodeForUpdate(anyString());
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("UTCID05 (A) - order not found -> RspCode 01")
        void handleIpn_orderNotFound_returns01() {
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 10_000L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.empty());

            assertEquals("01", service.handleIpn(params).rspCode());
            verify(enrollmentRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        @DisplayName("UTCID06 (A) - amount 999 mismatches -> RspCode 04")
        void handleIpn_amountMismatch_returns04() {
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 999L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));

            assertEquals("04", service.handleIpn(params).rspCode());
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
        }

        @Test
        @org.junit.jupiter.api.Order(7)
        @DisplayName("UTCID07 (A) - order already PAID -> RspCode 02, replay safe")
        void handleIpn_alreadyPaidOrder_isIdempotent_returns02() {
            order.setStatus(OrderStatus.PAID);
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 10_000L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));

            IpnAckResponse ack = service.handleIpn(params);

            assertEquals("02", ack.rspCode());
            // No side effects on a replayed callback.
            verify(paymentTransactionRepository, never()).save(any());
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
            verify(eventPublisher, never()).publishEvent(any(PaymentNotificationEvent.class));
        }

        @Test
        @org.junit.jupiter.api.Order(8)
        @DisplayName("UTCID08 (N) - provider response 24 -> order CANCELLED + failure notify")
        void handleIpn_cancelledPaymentAtProvider_recordsCancelledAndReturns00() {
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, false, 10_000L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));
            when(paymentTransactionRepository.findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
                    order.getId(), "VNPAY"))
                    .thenReturn(Optional.of(PaymentTransaction.builder()
                            .order(order).provider("VNPAY").status(PaymentStatus.PENDING).build()));

            IpnAckResponse ack = service.handleIpn(params);

            assertEquals("00", ack.rspCode());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
            verify(eventPublisher).publishEvent(any(PaymentNotificationEvent.class));
        }

        @Test
        @org.junit.jupiter.api.Order(9)
        @DisplayName("UTCID09 (B) - amount 9999 = expected - 1 -> RspCode 04")
        void handleIpn_amountOneMinorUnitBelowExpected_returns04() {
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 9_999L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));

            assertEquals("04", service.handleIpn(params).rspCode());
            assertEquals(OrderStatus.PENDING, order.getStatus());
            verify(escrowService, never()).holdForOrder(any());
        }

        @Test
        @org.junit.jupiter.api.Order(10)
        @DisplayName("UTCID10 (B) - amount 10001 = expected + 1 -> RspCode 04")
        void handleIpn_amountOneMinorUnitAboveExpected_returns04() {
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 10_001L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));

            assertEquals("04", service.handleIpn(params).rspCode());
            assertEquals(OrderStatus.PENDING, order.getStatus());
            verify(escrowService, never()).holdForOrder(any());
        }

        @Test
        @org.junit.jupiter.api.Order(11)
        @DisplayName("UTCID11 (N) - signed browser return 24 -> CANCELLED and wallet released")
        void handleVnPayReturn_cancelledPayment_closesOrderAndReleasesWallet() {
            PaymentTransaction gatewayPayment = PaymentTransaction.builder()
                    .order(order).provider("VNPAY").status(PaymentStatus.PENDING).build();
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, false, 10_000L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));
            when(paymentTransactionRepository.findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
                    order.getId(), "VNPAY")).thenReturn(Optional.of(gatewayPayment));
            when(paymentTransactionRepository.findByOrder_IdAndStatusInOrderByCreatedAtAsc(
                    eq(order.getId()), any())).thenReturn(List.of());

            IpnAckResponse ack = service.handleVnPayReturn(params);

            assertEquals("00", ack.rspCode());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            assertEquals(PaymentStatus.FAILED, gatewayPayment.getStatus());
            verify(studentWalletService).releaseForOrder(eq(order.getId()), any(Instant.class));
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
        }

        @Test
        @org.junit.jupiter.api.Order(12)
        @DisplayName("UTCID12 (A) - late successful IPN cannot reopen cancelled order")
        void handleIpn_lateSuccessAfterCancellation_doesNotReopenOrder() {
            order.setStatus(OrderStatus.CANCELLED);
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 10_000L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));

            IpnAckResponse ack = service.handleIpn(params);

            assertEquals("02", ack.rspCode());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            verify(paymentTransactionRepository, never()).save(any());
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
        }

        @Test
        @org.junit.jupiter.api.Order(13)
        @DisplayName("UTCID13 (N) - pending VNPay payment older than TTL -> CANCELLED")
        void expirePendingPayments_oldPendingPayment_closesOrder() {
            PaymentTransaction pendingPayment = PaymentTransaction.builder()
                    .order(order).provider("VNPAY").status(PaymentStatus.PENDING).build();
            when(orderRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                    eq(OrderStatus.PENDING), any(Instant.class)))
                    .thenReturn(List.of(order));
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(paymentTransactionRepository.findByOrder_IdAndStatusInOrderByCreatedAtAsc(
                    eq(order.getId()), any())).thenReturn(List.of(pendingPayment));

            service.expirePendingPayments();

            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            assertEquals(PaymentStatus.FAILED, pendingPayment.getStatus());
            verify(studentWalletService).releaseForOrder(eq(order.getId()), any(Instant.class));
        }

        @Test
        @org.junit.jupiter.api.Order(14)
        @DisplayName("UTCID14 (N) - signed browser return with another error -> FAILED")
        void handleVnPayReturn_otherProviderError_marksOrderFailed() {
            PaymentTransaction gatewayPayment = PaymentTransaction.builder()
                    .order(order).provider("VNPAY").status(PaymentStatus.PENDING).build();
            when(paymentGateway.parseCallback(params)).thenReturn(new PaymentCallbackResult(
                    true, "OD1", "99999", 10_000L, "51", "02", false));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));
            when(paymentTransactionRepository.findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
                    order.getId(), "VNPAY")).thenReturn(Optional.of(gatewayPayment));
            when(paymentTransactionRepository.findByOrder_IdAndStatusInOrderByCreatedAtAsc(
                    eq(order.getId()), any())).thenReturn(List.of());

            assertEquals("00", service.handleVnPayReturn(params).rspCode());
            assertEquals(OrderStatus.FAILED, order.getStatus());
        }

        @Test
        @org.junit.jupiter.api.Order(15)
        @DisplayName("UTCID15 (A) - invalid browser return checksum -> no state change")
        void handleVnPayReturn_invalidSignature_returns97AndDoesNothing() {
            when(paymentGateway.parseCallback(params)).thenReturn(result(false, false, 10_000L));

            assertEquals("97", service.handleVnPayReturn(params).rspCode());
            assertEquals(OrderStatus.PENDING, order.getStatus());
            verify(orderRepository, never()).findByOrderCodeForUpdate(anyString());
            verify(paymentTransactionRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(16)
        @DisplayName("UTCID16 (N) - signed successful browser return confirms pending order")
        void handleVnPayReturn_success_confirmsPaymentAndFulfilsOrder() {
            PaymentTransaction gatewayPayment = PaymentTransaction.builder()
                    .order(order).provider("VNPAY").status(PaymentStatus.PENDING).build();
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 10_000L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));
            when(paymentTransactionRepository.findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
                    order.getId(), "VNPAY")).thenReturn(Optional.of(gatewayPayment));
            when(orderItemRepository.findByOrder_Id(order.getId()))
                    .thenReturn(List.of(OrderItem.builder()
                            .order(order).course(course).price(new BigDecimal("100.00")).build()));
            when(enrollmentRepository.findByStudentIdAndCourseIdForUpdate(any(), any()))
                    .thenReturn(Optional.empty());

            IpnAckResponse ack = service.handleVnPayReturn(params);

            assertEquals("00", ack.rspCode());
            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(PaymentStatus.SUCCESS, gatewayPayment.getStatus());
            assertEquals("99999", gatewayPayment.getProviderTransactionId());
            verify(enrollmentRepository).save(any(Enrollment.class));
            verify(escrowService).holdForOrder(order);
            verify(eventPublisher).publishEvent(any(PaymentNotificationEvent.class));
        }

        @Test
        @org.junit.jupiter.api.Order(17)
        @DisplayName("UTCID17 (A) - signed return after IPN is replay-safe for frontend")
        void handleVnPayReturn_alreadyPaid_returns00WithoutDuplicateFulfilment() {
            order.setStatus(OrderStatus.PAID);
            when(paymentGateway.parseCallback(params)).thenReturn(result(true, true, 10_000L));
            when(orderRepository.findByOrderCodeForUpdate("OD1")).thenReturn(Optional.of(order));

            IpnAckResponse ack = service.handleVnPayReturn(params);

            assertEquals("00", ack.rspCode());
            assertEquals(OrderStatus.PAID, order.getStatus());
            verify(paymentTransactionRepository, never()).save(any());
            verify(enrollmentRepository, never()).save(any());
            verify(escrowService, never()).holdForOrder(any());
            verify(eventPublisher, never()).publishEvent(any(PaymentNotificationEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Not part of Report 5.1 — kept from the earlier iteration
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("(khong thuoc sheet nao) - initiatePayment / initiateCombinedPayment")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InitiatePayment {

        @Test
        @org.junit.jupiter.api.Order(1)
        void initiateCombinedPayment_partialBalance_setsWalletPortionAndChargesRemainder() {
            when(studentWalletService.getOrCreateStudentWallet(any()))
                    .thenReturn(Wallet.builder()
                            .id(UUID.randomUUID())
                            .balance(new BigDecimal("40.00"))
                            .frozenBalance(BigDecimal.ZERO)
                            .build());
            when(studentWalletService.reserveForOrder(any(), any(), any(), any()))
                    .thenReturn(WalletPaymentReservation.builder()
                            .id(UUID.randomUUID())
                            .orderId(order.getId())
                            .amount(new BigDecimal("40.00"))
                            .status(WalletReservationStatus.RESERVED)
                            .build());
            when(paymentGateway.buildPaymentUrl(eq(order), anyString())).thenReturn("https://vnpay/pay");

            String url = service.initiateCombinedPayment(order, "1.2.3.4");

            assertEquals("https://vnpay/pay", url);
            assertEquals(new BigDecimal("40.00"), order.getWalletAmount());
            assertEquals(new BigDecimal("60.00"), order.getGatewayAmount());
            verify(studentWalletService).reserveForOrder(
                    eq(order.getStudent().getId()), eq(order.getId()),
                    eq(new BigDecimal("40.00")), any(Instant.class));
            verify(orderRepository).save(order);
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        void initiatePayment_savesPendingTransactionAndReturnsPaymentUrl() {
            when(paymentGateway.buildPaymentUrl(order, "1.2.3.4")).thenReturn("https://sandbox.vnpayment.vn/pay?x=1");

            String url = service.initiatePayment(order, "1.2.3.4");

            assertEquals("https://sandbox.vnpayment.vn/pay?x=1", url);
            ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
            verify(paymentTransactionRepository).save(txCaptor.capture());
            assertEquals(PaymentStatus.PENDING, txCaptor.getValue().getStatus());
            assertEquals(new BigDecimal("100.00"), txCaptor.getValue().getAmount());
        }
    }
}
