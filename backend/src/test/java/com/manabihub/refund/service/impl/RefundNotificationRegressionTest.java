package com.manabihub.refund.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
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
import com.manabihub.refund.enums.StudentRefundType;
import com.manabihub.refund.repository.RefundProviderAttemptRepository;
import com.manabihub.refund.repository.RefundRequestRepository;
import com.manabihub.refund.service.RefundAfterCommitNotifier;
import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.EscrowStatus;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.service.EscrowService;
import com.manabihub.wallet.service.StudentWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for refund notification delivery with missing users.
 * Verifies that RefundAfterCommitNotifier handles null recipients independently
 * and that the settlement transaction is never rolled back by notification failures.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundNotificationRegressionTest {

    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private RefundProviderAttemptRepository attemptRepository;
    @Mock private InternalAdminAccountRepository adminAccountRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderItemSnapshotRepository snapshotRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private com.manabihub.learning.service.EnrollmentAccessService enrollmentAccessService;
    @Mock private EscrowLedgerRepository escrowLedgerRepository;
    @Mock private EscrowService escrowService;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;
    @Mock private StudentWalletService studentWalletService;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private RefundAfterCommitNotifier afterCommitNotifier;

    @InjectMocks
    private RefundDecisionTransactionService service;

    private UUID adminId;
    private RefundRequest refund;
    private Order order;
    private OrderItem refundedItem;
    private OrderItem otherItem;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        afterCommitNotifier = new RefundAfterCommitNotifier(notificationService, transactionManager);

        // Manually inject the real notifier (since @InjectMocks won't pick the manually created bean)
        service = new RefundDecisionTransactionService(
                refundRequestRepository,
                attemptRepository,
                adminAccountRepository,
                orderRepository,
                orderItemRepository,
                snapshotRepository,
                paymentTransactionRepository,
                enrollmentRepository,
                enrollmentAccessService,
                escrowLedgerRepository,
                escrowService,
                auditLogService,
                afterCommitNotifier,
                studentWalletService
        );

        adminId = UUID.randomUUID();
        InternalAdminAccount admin = financeAdmin(adminId);
        when(adminAccountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminAccountRepository.hasPermission(adminId, "REFUND_REVIEW")).thenReturn(true);

        StudentProfile student = student();
        Course course = course();

        order = Order.builder()
                .id(UUID.randomUUID())
                .student(student)
                .orderCode("ORD-NOTIF-001")
                .totalAmount(money("1000000"))
                .currency("VND")
                .status(OrderStatus.PAID)
                .build();
        refundedItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .course(course)
                .price(money("1000000"))
                .refundStatus(OrderItemRefundStatus.NOT_REFUNDED)
                .build();
        otherItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .course(course)
                .price(money("500000"))
                .refundStatus(OrderItemRefundStatus.NOT_REFUNDED)
                .build();
        refund = RefundRequest.builder()
                .id(UUID.randomUUID())
                .order(order)
                .orderItem(refundedItem)
                .student(student)
                .status(RefundStatus.PENDING)
                .providerStatus(RefundProviderStatus.NOT_REQUESTED)
                .reason("Test refund")
                .eligibilitySnapshot(com.manabihub.refund.dto.RefundEligibilitySnapshot.builder()
                        .orderId(order.getId())
                        .orderItemId(refundedItem.getId())
                        .actuallyPaidAmount(money("1000000"))
                        .policyVersion("refund-policy-v1")
                        .paymentSucceededAt(Instant.parse("2026-07-20T00:00:00Z"))
                        .requestedAt(Instant.parse("2026-07-21T00:00:00Z"))
                        .measuredProgressPercent(5.0)
                        .refundType(StudentRefundType.STANDARD)
                        .eligible(true)
                        .eligibilityResult(EligibilityResult.STANDARD_ELIGIBLE)
                        .build())
                .build();

        PaymentTransaction payment = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .order(order)
                .provider("VNPAY")
                .providerTransactionId("VNP-NOTIF-001")
                .amount(money("1000000"))
                .status(PaymentStatus.SUCCESS)
                .succeededAt(Instant.parse("2026-07-20T00:00:00Z"))
                .build();
        enrollment = Enrollment.builder()
                .id(UUID.randomUUID())
                .student(student)
                .course(course)
                .status(EnrollmentStatus.ACTIVE)
                .build();

        OrderItemSnapshot snapshot = OrderItemSnapshot.builder()
                .id(UUID.randomUUID())
                .orderItem(refundedItem)
                .currency("VND")
                .grossAmount(money("1000000"))
                .commissionRate(new BigDecimal("0.2000"))
                .commissionAmount(money("200000"))
                .teacherNetAmount(money("800000"))
                .commercialPolicyVersion("commercial-v1")
                .escrowDays(7)
                .build();
        EscrowLedger escrow = EscrowLedger.builder()
                .id(UUID.randomUUID())
                .order(order)
                .orderItem(refundedItem)
                .course(course)
                .teacher(course.getTeacher())
                .amount(money("800000"))
                .status(EscrowStatus.HELD)
                .build();

        when(refundRequestRepository.findByIdForUpdate(refund.getId()))
                .thenReturn(Optional.of(refund));
        when(orderRepository.findByIdForUpdate(order.getId()))
                .thenReturn(Optional.of(order));
        when(snapshotRepository.findByOrderItem_Id(refundedItem.getId()))
                .thenReturn(Optional.of(snapshot));
        when(paymentTransactionRepository.findByOrder_IdAndStatusInOrderByCreatedAtAsc(
                order.getId(), List.of(PaymentStatus.SUCCESS)))
                .thenReturn(List.of(payment));
        when(escrowLedgerRepository.findByOrderItemIdForUpdate(refundedItem.getId()))
                .thenReturn(Optional.of(escrow));

        WalletTransaction walletCredit = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .build();
        when(escrowService.reverseHeldAllocationForRefund(refundedItem.getId()))
                .thenReturn(true);
        when(studentWalletService.creditRefund(
                eq(refund.getStudent().getId()),
                eq(money("1000000")),
                eq(refund.getId()),
                anyString()))
                .thenReturn(walletCredit);
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(
                refund.getStudent().getId(), refundedItem.getCourse().getId()))
                .thenReturn(Optional.of(enrollment));
        when(orderItemRepository.findByOrder_Id(order.getId()))
                .thenReturn(List.of(refundedItem, otherItem));
    }

    @Test
    @DisplayName("Regression #1: missing student user → approval commits, teacher still gets notification")
    void missingStudentUserDoesNotBlockTeacherNotification() {
        // Set student user to null
        refund.getStudent().setUser(null);

        RefundStatus outcome = service.approveToStudentWallet(
                refund.getId(), approvalDecision(), adminId);

        assertEquals(RefundStatus.APPROVED, outcome);
        // Teacher notification should still fire
        verify(notificationService).createNotificationOnce(
                contains("teacher"),
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                eq(NotificationTypes.REFUND),
                eq("/teacher/wallet")
        );
        // Student notification should NOT fire (no NPE)
        verify(notificationService, never()).createNotificationOnce(
                contains("student"),
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                eq(NotificationTypes.REFUND),
                eq("/student/payments")
        );
    }

    @Test
    @DisplayName("Regression #2: missing teacher user → approval commits, student still gets notification")
    void missingTeacherUserDoesNotBlockStudentNotification() {
        // Set teacher user to null
        refundedItem.getCourse().getTeacher().setUser(null);

        RefundStatus outcome = service.approveToStudentWallet(
                refund.getId(), approvalDecision(), adminId);

        assertEquals(RefundStatus.APPROVED, outcome);
        // Student notification should still fire
        verify(notificationService).createNotificationOnce(
                contains("student"),
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                eq(NotificationTypes.REFUND),
                eq("/student/payments")
        );
        // Teacher notification should NOT fire (no NPE)
        verify(notificationService, never()).createNotificationOnce(
                contains("teacher"),
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                eq(NotificationTypes.REFUND),
                eq("/teacher/wallet")
        );
    }

    @Test
    @DisplayName("Regression #3: both users missing → approval still commits, no double credit")
    void bothUsersMissingStillCommitsWithoutDoubleCreditOrNpe() {
        refund.getStudent().setUser(null);
        refundedItem.getCourse().getTeacher().setUser(null);

        RefundStatus outcome = service.approveToStudentWallet(
                refund.getId(), approvalDecision(), adminId);

        assertEquals(RefundStatus.APPROVED, outcome);
        // Wallet was credited exactly once
        verify(studentWalletService, times(1)).creditRefund(any(), any(), any(), anyString());
        // No notification should fire at all (no NPE either)
        verify(notificationService, never()).createNotificationOnce(
                anyString(), any(UUID.class), anyString(), anyString(), anyString(),
                any(), anyString()
        );
    }

    @Test
    @DisplayName("Regression #4: legacy admin role null → approval settles without 500")
    void legacyAdminRoleNullDoesNotCause500() {
        InternalAdminAccount legacyAdmin = financeAdmin(adminId);
        legacyAdmin.setRole(null);
        when(adminAccountRepository.findById(adminId)).thenReturn(Optional.of(legacyAdmin));

        RefundStatus outcome = service.approveToStudentWallet(
                refund.getId(), approvalDecision(), adminId);

        assertEquals(RefundStatus.APPROVED, outcome);
        verify(auditLogService).logAdminAction(
                eq(adminId),
                eq("INTERNAL_ADMIN"),
                eq("APPROVE_REFUND_TO_WALLET"),
                anyString(), any(UUID.class), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Regression #5: duplicate approve → idempotent, wallet credited only once")
    void duplicateApproveIsIdempotentWalletCreditedOnce() {
        // First approval
        service.approveToStudentWallet(refund.getId(), approvalDecision(), adminId);
        assertEquals(RefundStatus.APPROVED, refund.getStatus());

        // Second approval — should be idempotent
        RefundStatus second = service.approveToStudentWallet(
                refund.getId(), approvalDecision(), adminId);

        assertEquals(RefundStatus.APPROVED, second);
        // Wallet credited exactly once
        verify(studentWalletService, times(1)).creditRefund(any(), any(), any(), anyString());
        // Escrow reversed exactly once
        verify(escrowService, times(1)).reverseHeldAllocationForRefund(any());
    }

    // ──────────────────────────────────────────────
    // Fixtures
    // ──────────────────────────────────────────────

    private RefundDecisionRequest approvalDecision() {
        return RefundDecisionRequest.builder()
                .reasonCode(RefundDecisionReason.STANDARD_ELIGIBLE)
                .note("Regression test approval.")
                .build();
    }

    private InternalAdminAccount financeAdmin(UUID id) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setCode(RoleCode.FINANCE_MANAGER);
        role.setName("Finance Manager");
        InternalAdminAccount admin = new InternalAdminAccount();
        admin.setId(id);
        admin.setEmail("finance@manabihub.test");
        admin.setFullName("Finance Reviewer");
        admin.setPasswordHash("unused");
        admin.setRole(role);
        return admin;
    }

    private StudentProfile student() {
        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .email("student@manabihub.test")
                .fullName("Student")
                .build();
        return StudentProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .displayName("Student")
                .build();
    }

    private Course course() {
        com.manabihub.kyc.domain.AppUser teacherUser =
                new com.manabihub.kyc.domain.AppUser();
        teacherUser.setId(UUID.randomUUID());
        teacherUser.setEmail("teacher@manabihub.test");
        teacherUser.setFullName("Teacher");
        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(UUID.randomUUID());
        teacher.setUser(teacherUser);
        return Course.builder()
                .id(UUID.randomUUID())
                .teacher(teacher)
                .title("Notification-safe course")
                .slug("notification-safe-course")
                .price(money("1000000"))
                .currency("VND")
                .build();
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
