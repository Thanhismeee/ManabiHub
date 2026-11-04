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
import com.manabihub.notification.NotificationTypes;
import com.manabihub.payment.gateway.PaymentCallbackResult;
import com.manabihub.payment.gateway.PaymentGateway;
import com.manabihub.payment.repository.PaymentTransactionRepository;
import com.manabihub.payment.service.PaymentService;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.service.EscrowService;
import com.manabihub.wallet.service.StudentWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final BigDecimal MINOR_UNIT_FACTOR = BigDecimal.valueOf(100);
    private static final Duration WALLET_RESERVATION_TTL = Duration.ofMinutes(15);
    private static final String WALLET_PROVIDER = "WALLET";

    private final PaymentGateway paymentGateway;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentProgressResetService enrollmentProgressResetService;
    private final com.manabihub.learning.service.EnrollmentAccessService enrollmentAccessService;
    private final EscrowService escrowService;
    private final StudentWalletService studentWalletService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final VnPayProperties vnPayProperties;

    @Override
    @Transactional
    public String initiatePayment(Order order, String clientIp) {
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .order(order)
                .provider(paymentGateway.getProvider())
                .amount(order.getGatewayAmount())
                .status(PaymentStatus.PENDING)
                .build());

        return paymentGateway.buildPaymentUrl(order, clientIp);
    }

    @Override
    @Transactional
    public IpnAckResponse handleIpn(Map<String, String> params) {
        PaymentCallbackResult result = paymentGateway.parseCallback(params);
        if (!result.signatureValid()) {
            log.warn("[{}] Rejected payment IPN with invalid checksum, txnRef={}",
                    MessageCodes.MSG_PAY_004, result.orderCode());
            return IpnAckResponse.of("97", "Invalid Checksum");
        }

        return processVerifiedProviderCallback(params, result, false);
    }

    /**
     * Applies a callback only after its VNPay HMAC has been verified. Browser returns and
     * IPNs share this transaction so either arrival order remains idempotent. The browser
     * redirect is therefore a safe fallback for demo/sandbox environments where VNPay
     * cannot reach the IPN endpoint, without trusting any unsigned client-side value.
     */
    private IpnAckResponse processVerifiedProviderCallback(
            Map<String, String> params,
            PaymentCallbackResult result,
            boolean browserReturn
    ) {

        // Pessimistic lock serializes concurrent provider callbacks for the same order,
        // which — together with the PAID status check below — makes processing idempotent.
        Order order = orderRepository.findByOrderCodeForUpdate(result.orderCode()).orElse(null);
        if (order == null) {
            return IpnAckResponse.of("01", "Order not Found");
        }

        long expectedMinor = order.getGatewayAmount().multiply(MINOR_UNIT_FACTOR).longValue();
        if (result.amount() != expectedMinor) {
            log.warn("[{}] Payment callback amount mismatch for order {}: expected {}, got {}",
                    MessageCodes.MSG_PAY_004, order.getOrderCode(), expectedMinor, result.amount());
            return IpnAckResponse.of("04", "Invalid Amount");
        }

        if (order.getStatus() == OrderStatus.PAID) {
            // Duplicate/replayed callback — already processed.
            log.info("[{}] Duplicate payment callback for already-paid order {}",
                    MessageCodes.MSG_PAY_005, order.getOrderCode());
            // VNPay expects 02 for duplicate IPNs. The browser endpoint returns 00
            // so the frontend can poll and render the already-paid order.
            return browserReturn
                    ? IpnAckResponse.of("00", "Payment already confirmed")
                    : IpnAckResponse.of("02", "Order already confirmed");
        }

        // A cancelled/failed/expired order is terminal. A late success callback
        // must never reopen it or create a second enrollment/escrow hold.
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("[{}] Ignored payment callback for closed order {} (status={})",
                    MessageCodes.MSG_PAY_005, order.getOrderCode(), order.getStatus());
            return IpnAckResponse.of("02", "Order already closed");
        }

        PaymentTransaction transaction = latestOrNewGatewayTransaction(order);
        transaction.setProviderTransactionId(result.providerTransactionId());
        transaction.setRawResponse(objectMapper.valueToTree(params));

        if (!result.paymentSuccessful()) {
            transaction.setStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(transaction);
            OrderStatus terminalStatus = "24".equals(result.responseCode())
                    ? OrderStatus.CANCELLED
                    : OrderStatus.FAILED;
            closePendingOrder(order, terminalStatus, Instant.now());
            notifyPaymentFailed(order);
            log.info("[{}] Recorded {} payment for order {} (responseCode={})",
                    MessageCodes.MSG_PAY_003, terminalStatus, order.getOrderCode(), result.responseCode());
            return IpnAckResponse.of("00", "Confirm Success");
        }

        Instant succeededAt = transaction.getSucceededAt() == null
                ? Instant.now()
                : transaction.getSucceededAt();
        transaction.setStatus(PaymentStatus.SUCCESS);
        transaction.setSucceededAt(succeededAt);
        paymentTransactionRepository.save(transaction);

        // Fulfilment depends on what the order is for (no longer hardcoded to course purchase).
        if (order.getType() == OrderType.WALLET_TOPUP) {
            fulfillWalletTopUp(order);
            log.info("[{}] Confirmed wallet top-up for order {} — balance credited",
                    MessageCodes.MSG_PAY_002, order.getOrderCode());
        } else {
            // The wallet share was frozen before redirecting to VNPay. Capture that
            // reservation now; it can no longer be spent by a concurrent order.
            if (order.getWalletAmount() != null && order.getWalletAmount().signum() > 0) {
                try {
                    WalletTransaction walletDebit = studentWalletService
                            .captureForOrder(order.getId(), succeededAt);
                    succeedWalletComponent(order, walletDebit, succeededAt);
                } catch (BusinessException captureFailure) {
                    order.setStatus(OrderStatus.FAILED);
                    orderRepository.save(order);
                    notifyPaymentFailed(order);
                    log.error(
                            "[{}] Gateway payment succeeded but wallet reservation capture requires reconciliation for order {}",
                            MessageCodes.MSG_PAY_004,
                            order.getOrderCode(),
                            captureFailure);
                    return IpnAckResponse.of("00", "Confirm Success");
                }
            }
            // Escrow allocation requires a persisted PAID order. This write remains
            // in the same transaction, so a fulfilment error rolls everything back.
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            fulfillCourseOrder(order);
            log.info("[{}] Confirmed payment for order {} — enrollment + escrow created",
                    MessageCodes.MSG_PAY_002, order.getOrderCode());
        }
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        return IpnAckResponse.of("00", "Confirm Success");
    }

    @Override
    @Transactional
    public IpnAckResponse handleVnPayReturn(Map<String, String> params) {
        PaymentCallbackResult result = paymentGateway.parseCallback(params);
        if (!result.signatureValid()) {
            log.warn("[{}] Rejected VNPay browser return with invalid checksum, txnRef={}",
                    MessageCodes.MSG_PAY_004, result.orderCode());
            return IpnAckResponse.of("97", "Invalid Checksum");
        }

        return processVerifiedProviderCallback(params, result, true);
    }

    @Override
    @Transactional
    public void expirePendingPayments() {
        long expiryMinutes = Math.max(1, vnPayProperties.getPaymentExpiryMinutes());
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(expiryMinutes));
        orderRepository
                .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus.PENDING, cutoff)
                .forEach(order -> expirePendingOrder(order.getId()));
    }

    private void expirePendingOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        closePendingOrder(order, OrderStatus.CANCELLED, Instant.now());
        log.info("[{}] Expired pending payment order {} after {} minutes",
                MessageCodes.MSG_PAY_003, order.getOrderCode(), vnPayProperties.getPaymentExpiryMinutes());
    }

    /**
     * Closes a pending order exactly once and releases any wallet reservation.
     * The caller must already hold the order row lock when this is used from a
     * callback; the expiry path acquires that lock before calling it.
     */
    private void closePendingOrder(Order order, OrderStatus terminalStatus, Instant closedAt) {
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        paymentTransactionRepository
                .findByOrder_IdAndStatusInOrderByCreatedAtAsc(
                        order.getId(), List.of(PaymentStatus.PENDING))
                .forEach(payment -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentTransactionRepository.save(payment);
                });
        studentWalletService.releaseForOrder(order.getId(), closedAt);
        order.setStatus(terminalStatus);
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order payWithWallet(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.ORDER_NOT_FOUND,
                        "Order was not found",
                        HttpStatus.NOT_FOUND));

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("[{}] Ignored duplicate wallet payment for already-paid order {}",
                    MessageCodes.MSG_PAY_005, order.getOrderCode());
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                    MessageCodes.COMMON_CONFLICT,
                    "Only a pending order can be paid with wallet",
                    HttpStatus.CONFLICT);
        }

        Instant succeededAt = Instant.now();
        order.setWalletAmount(order.getTotalAmount());
        orderRepository.save(order);
        studentWalletService.reserveForOrder(
                order.getStudent().getId(),
                order.getId(),
                order.getTotalAmount(),
                succeededAt.plus(WALLET_RESERVATION_TTL));
        WalletTransaction debit = studentWalletService.captureForOrder(order.getId(), succeededAt);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .order(order)
                .provider(WALLET_PROVIDER)
                .providerTransactionId(debit.getId().toString())
                .amount(order.getTotalAmount())
                .status(PaymentStatus.SUCCESS)
                .succeededAt(succeededAt)
                .build();
        paymentTransactionRepository.save(transaction);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        fulfillCourseOrder(order);

        log.info("[{}] Paid order {} with wallet — enrollment + escrow created",
                MessageCodes.MSG_PAY_002, order.getOrderCode());
        return order;
    }

    @Override
    @Transactional
    public void cancelPendingOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.ORDER_NOT_FOUND,
                        "Order was not found",
                        HttpStatus.NOT_FOUND));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                    MessageCodes.COMMON_CONFLICT,
                    "Only a pending order can be cancelled",
                    HttpStatus.CONFLICT);
        }

        closePendingOrder(order, OrderStatus.CANCELLED, Instant.now());
        log.info("[{}] Cancelled pending order {} at the student's request",
                MessageCodes.MSG_PAY_003, order.getOrderCode());
    }

    @Override
    @Transactional
    public String initiateCombinedPayment(Order order, String clientIp) {
        BigDecimal balance = studentWalletService
                .getOrCreateStudentWallet(order.getStudent().getId())
                .getAvailableBalance();
        BigDecimal total = order.getTotalAmount();

        if (balance.compareTo(total) >= 0) {
            // Wallet fully covers the order — pay entirely from wallet, no gateway.
            Order paidOrder = payWithWallet(order.getId());
            order.setStatus(paidOrder.getStatus());
            return null;
        }
        if (balance.signum() > 0) {
            // Freeze the wallet share before constructing the gateway request. If a
            // concurrent order reserved it first, this fails before VNPay can charge.
            BigDecimal walletAmount = balance.min(total);
            var reservation = studentWalletService.reserveForOrder(
                    order.getStudent().getId(),
                    order.getId(),
                    walletAmount,
                    Instant.now().plus(WALLET_RESERVATION_TTL));
            order.setWalletAmount(reservation.getAmount());
            orderRepository.save(order);
            paymentTransactionRepository.save(PaymentTransaction.builder()
                    .order(order)
                    .provider(WALLET_PROVIDER)
                    .providerTransactionId(reservation.getId().toString())
                    .amount(reservation.getAmount())
                    .status(PaymentStatus.PENDING)
                    .build());
        }
        return initiatePayment(order, clientIp);
    }

    /** Fulfils a paid COURSE order: enrollment(s) + teacher escrow hold + student notification. */
    private void fulfillCourseOrder(Order order) {
        createEnrollments(order);
        escrowService.holdForOrder(order);
        notifyStudent(order);
        notifyTeachers(order);
    }

    private void fulfillWalletTopUp(Order order) {
        StudentProfile student = order.getStudent();
        studentWalletService.creditTopUp(
                student.getId(),
                order.getTotalAmount(),
                order.getId(),
                "Nạp ví qua đơn " + order.getOrderCode());
        notifyTopUp(order);
    }

    private PaymentTransaction latestOrNewGatewayTransaction(Order order) {
        PaymentTransaction existing = paymentTransactionRepository
                .findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
                        order.getId(),
                        paymentGateway.getProvider())
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        return PaymentTransaction.builder()
                .order(order)
                .provider(paymentGateway.getProvider())
                .amount(order.getGatewayAmount())
                .status(PaymentStatus.PENDING)
                .build();
    }

    private void succeedWalletComponent(
            Order order,
            WalletTransaction debit,
            Instant succeededAt
    ) {
        PaymentTransaction walletPayment = paymentTransactionRepository
                .findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(order.getId(), WALLET_PROVIDER)
                .orElseGet(() -> PaymentTransaction.builder()
                        .order(order)
                        .provider(WALLET_PROVIDER)
                        .amount(order.getWalletAmount())
                        .build());
        walletPayment.setProviderTransactionId(debit.getId().toString());
        walletPayment.setAmount(order.getWalletAmount());
        walletPayment.setStatus(PaymentStatus.SUCCESS);
        walletPayment.setSucceededAt(succeededAt);
        paymentTransactionRepository.save(walletPayment);
    }

    private void createEnrollments(Order order) {
        StudentProfile student = order.getStudent();
        for (OrderItem item : orderItemRepository.findByOrder_Id(order.getId())) {
            enrollmentAccessService.completePurchase(student, item.getCourse(), item.getId());
        }
    }

    private void notifyStudent(Order order) {
        StudentProfile student = order.getStudent();
        AppUser user = student.getUser();
        eventPublisher.publishEvent(new PaymentNotificationEvent(
                order.getId(),
                user.getId(),
                user.getEmail(),
                "Mua khoá học thành công",
                "Đơn hàng " + order.getOrderCode()
                        + " đã được thanh toán thành công. Bạn có thể bắt đầu học ngay bây giờ.",
                NotificationTypes.PURCHASE_SUCCESS,
                "/student/courses"));
    }

    private void notifyTopUp(Order order) {
        StudentProfile student = order.getStudent();
        AppUser user = student.getUser();
        eventPublisher.publishEvent(new PaymentNotificationEvent(
                order.getId(),
                user.getId(),
                user.getEmail(),
                "Nạp ví thành công",
                "Đơn nạp ví " + order.getOrderCode() + " ("
                        + order.getTotalAmount().toPlainString() + "đ) đã được xử lý thành công. "
                        + "Số dư ví của bạn đã được cập nhật.",
                NotificationTypes.WALLET_TOPUP_SUCCESS,
                "/student/wallet"));
    }

    private void notifyPaymentFailed(Order order) {
        AppUser user = order.getStudent().getUser();
        eventPublisher.publishEvent(new PaymentNotificationEvent(
                order.getId(),
                user.getId(),
                user.getEmail(),
                "Thanh toán chưa thành công",
                "Đơn hàng " + order.getOrderCode()
                        + " chưa được thanh toán. Vui lòng kiểm tra lại phương thức thanh toán.",
                NotificationTypes.PAYMENT_FAILED,
                "/student/payments"));
    }

    private void notifyTeachers(Order order) {
        orderItemRepository.findByOrder_Id(order.getId()).stream()
                .filter(item -> item.getCourse() != null
                        && item.getCourse().getTeacher() != null
                        && item.getCourse().getTeacher().getUser() != null)
                .collect(Collectors.groupingBy(item -> item.getCourse().getTeacher().getUser().getId()))
                .values()
                .forEach(items -> {
            var teacherUser = items.getFirst().getCourse().getTeacher().getUser();
            String courseTitles = items.stream()
                    .map(item -> item.getCourse().getTitle())
                    .collect(Collectors.joining(", "));
            BigDecimal grossAmount = items.stream()
                    .map(OrderItem::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            eventPublisher.publishEvent(new PaymentNotificationEvent(
                    order.getId(),
                    teacherUser.getId(),
                    teacherUser.getEmail(),
                    "Khóa học vừa có học viên mới",
                    "Học viên vừa mua khóa học: " + courseTitles
                            + ". Tổng giá trị trước phân bổ: " + grossAmount.toPlainString() + " VND.",
                    NotificationTypes.TEACHER_SALE,
                    "/teacher/wallet"));
        });
    }
}
