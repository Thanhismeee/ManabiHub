package com.manabihub.payment.repository;

import com.manabihub.payment.entity.PaymentTransaction;
import com.manabihub.payment.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    /** Idempotency lookup for an already-recorded provider transaction. */
    Optional<PaymentTransaction> findByProviderAndProviderTransactionId(String provider, String providerTransactionId);

    boolean existsByProviderAndProviderTransactionId(String provider, String providerTransactionId);

    List<PaymentTransaction> findByOrder_IdOrderByCreatedAtDesc(UUID orderId);

    Optional<PaymentTransaction> findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
            UUID orderId,
            String provider
    );

    List<PaymentTransaction> findByOrder_IdAndStatusInOrderByCreatedAtAsc(
            UUID orderId,
            List<PaymentStatus> statuses
    );

    Optional<PaymentTransaction> findFirstByOrder_IdAndSucceededAtIsNotNullOrderBySucceededAtDesc(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(
            UUID orderId,
            PaymentStatus status
    );
}
