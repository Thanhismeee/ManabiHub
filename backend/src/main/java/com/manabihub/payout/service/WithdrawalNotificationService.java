package com.manabihub.payout.service;

import com.manabihub.notification.service.NotificationService;
import com.manabihub.notification.NotificationTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WithdrawalNotificationService {

    private static final String TEACHER_WALLET_LINK = "/teacher/wallet";
    private static final String STUDENT_WALLET_LINK = "/student/wallet";

    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyFinanceManager(UUID withdrawalId, BigDecimal amount) {
        notificationService.createNotificationForAdminRole(
                "FINANCE_MANAGER",
                "Yêu cầu rút doanh thu mới",
                "Giáo viên vừa yêu cầu rút " + amount + " VND.",
                NotificationTypes.WITHDRAWAL_REQUESTED,
                "/admin/payouts/" + withdrawalId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyTeacherCancellation(
            UUID userId,
            String email,
            BigDecimal amount
    ) {
        notifyCancellation(userId, email, amount, TEACHER_WALLET_LINK);
    }

    /** Student cancellations resolve no email; the in-app notification is enough. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyCancellation(UUID userId, BigDecimal amount) {
        notifyCancellation(userId, null, amount, STUDENT_WALLET_LINK);
    }

    private void notifyCancellation(
            UUID userId,
            String email,
            BigDecimal amount,
            String walletLink
    ) {
        notificationService.createNotification(
                userId,
                email,
                "Đã hủy lệnh rút tiền",
                "Bạn đã hủy thành công lệnh rút tiền " + amount + " VND.",
                NotificationTypes.PAYOUT_CANCELLED,
                walletLink
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyFinanceManager(
            UUID withdrawalId,
            BigDecimal amount,
            String ownerType
    ) {
        if (!"STUDENT".equals(ownerType)) {
            notifyFinanceManager(withdrawalId, amount);
            return;
        }
        notificationService.createNotificationForAdminRole(
                "FINANCE_MANAGER",
                "Yêu cầu rút tiền học viên mới",
                "Học viên vừa yêu cầu rút " + amount + " VND.",
                NotificationTypes.WITHDRAWAL_REQUESTED,
                "/admin/payouts/" + withdrawalId
        );
    }

}
