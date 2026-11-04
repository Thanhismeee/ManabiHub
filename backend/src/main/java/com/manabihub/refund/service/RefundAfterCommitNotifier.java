package com.manabihub.refund.service;

import com.manabihub.notification.service.NotificationService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.refund.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Slf4j
@Component
public class RefundAfterCommitNotifier {

    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    public RefundAfterCommitNotifier(
            NotificationService notificationService,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationService = notificationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void schedule(
            UUID refundRequestId,
            RefundStatus outcome,
            Recipient student,
            Recipient teacher,
            String orderCode,
            String courseTitle
    ) {
        Runnable notificationTask = () -> notifyRecipients(
                refundRequestId,
                outcome,
                student,
                teacher,
                orderCode,
                courseTitle
        );
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationTask.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        notificationTask.run();
                    }
                }
        );
    }

    private void notifyRecipients(
            UUID refundRequestId,
            RefundStatus outcome,
            Recipient student,
            Recipient teacher,
            String orderCode,
            String courseTitle
    ) {
        boolean approved = outcome == RefundStatus.APPROVED;

        // Student and teacher notifications are independent: a failure in one
        // must never prevent delivery of the other.
        notifyStudent(refundRequestId, approved, student, courseTitle);
        notifyTeacher(refundRequestId, approved, teacher, orderCode, courseTitle);
    }

    private void notifyStudent(
            UUID refundRequestId,
            boolean approved,
            Recipient student,
            String courseTitle
    ) {
        if (student == null) {
            log.debug("Skipping student notification for refundId={}: recipient is null", refundRequestId);
            return;
        }
        try {
            String title = approved
                    ? "Yêu cầu hoàn tiền được xác nhận"
                    : "Yêu cầu hoàn tiền bị từ chối";
            String message = approved
                    ? "Khoản hoàn tiền cho " + courseTitle
                    + " đã được nhà cung cấp thanh toán xác nhận."
                    : "Yêu cầu hoàn tiền cho " + courseTitle
                    + " đã bị từ chối. Xem ghi chú quyết định để biết chi tiết.";
            String dedupeKey = "refund:" + refundRequestId + ":"
                    + (approved ? RefundStatus.APPROVED : RefundStatus.REJECTED) + ":student";

            transactionTemplate.executeWithoutResult(status ->
                notificationService.createNotificationOnce(
                        dedupeKey,
                        student.userId(),
                        student.email(),
                        title,
                        message,
                        NotificationTypes.REFUND,
                        "/student/payments"
                )
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Unable to deliver student refund notification for refundId={}, recipientType=student",
                    refundRequestId,
                    exception
            );
        }
    }

    private void notifyTeacher(
            UUID refundRequestId,
            boolean approved,
            Recipient teacher,
            String orderCode,
            String courseTitle
    ) {
        if (teacher == null) {
            log.debug("Skipping teacher notification for refundId={}: recipient is null", refundRequestId);
            return;
        }
        try {
            String title = approved
                    ? "Khoản phân bổ đã được hoàn lại"
                    : "Yêu cầu hoàn tiền đã được xử lý";
            String message = approved
                    ? "Đơn hàng " + orderCode + " cho " + courseTitle
                    + " đã được hoàn tiền; khoản phân bổ tương ứng đã được đảo."
                    : "Yêu cầu hoàn tiền của đơn " + orderCode + " cho "
                    + courseTitle + " đã bị từ chối; khoản phân bổ không thay đổi.";
            String dedupeKey = "refund:" + refundRequestId + ":"
                    + (approved ? RefundStatus.APPROVED : RefundStatus.REJECTED) + ":teacher";

            transactionTemplate.executeWithoutResult(status ->
                notificationService.createNotificationOnce(
                        dedupeKey,
                        teacher.userId(),
                        teacher.email(),
                        title,
                        message,
                        NotificationTypes.REFUND,
                        "/teacher/wallet"
                )
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Unable to deliver teacher refund notification for refundId={}, recipientType=teacher",
                    refundRequestId,
                    exception
            );
        }
    }

    public record Recipient(UUID userId, String email) {
    }
}
