package com.manabihub.wallet.job;

import com.manabihub.wallet.enums.WalletReservationStatus;
import com.manabihub.wallet.repository.WalletPaymentReservationRepository;
import com.manabihub.wallet.service.WalletReservationExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletReservationExpiryJob {

    private final WalletPaymentReservationRepository reservationRepository;
    private final WalletReservationExpiryService expiryService;

    @Scheduled(fixedDelayString = "${manabihub.wallet.reservation-expiry-scan-ms:60000}")
    public void releaseExpiredReservations() {
        Instant now = Instant.now();
        reservationRepository
                .findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        WalletReservationStatus.RESERVED,
                        now)
                .forEach(reservation -> {
                    try {
                        expiryService.expire(reservation.getOrderId(), now);
                    } catch (RuntimeException exception) {
                        log.error(
                                "Failed to release expired wallet reservation for order {}",
                                reservation.getOrderId(),
                                exception);
                    }
                });
    }
}
