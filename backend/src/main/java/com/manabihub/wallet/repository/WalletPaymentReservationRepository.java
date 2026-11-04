package com.manabihub.wallet.repository;

import com.manabihub.wallet.entity.WalletPaymentReservation;
import com.manabihub.wallet.enums.WalletReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletPaymentReservationRepository
        extends JpaRepository<WalletPaymentReservation, UUID> {

    Optional<WalletPaymentReservation> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reservation FROM WalletPaymentReservation reservation WHERE reservation.orderId = :orderId")
    Optional<WalletPaymentReservation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    List<WalletPaymentReservation> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            WalletReservationStatus status,
            Instant expiresAt
    );
}
