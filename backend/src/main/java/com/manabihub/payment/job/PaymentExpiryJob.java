package com.manabihub.payment.job;

import com.manabihub.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes payment attempts that have remained pending beyond the VNPay validity
 * window. The service acquires the order lock and performs the wallet release
 * and transaction updates atomically for every candidate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryJob {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${manabihub.payment.vnpay.expiry-scan-ms:60000}")
    public void expirePendingPayments() {
        try {
            paymentService.expirePendingPayments();
        } catch (RuntimeException exception) {
            log.error("Failed to expire pending VNPay payments", exception);
        }
    }
}
