package com.manabihub.refund.repository;

import com.manabihub.refund.entity.RefundProviderAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefundProviderAttemptRepository
        extends JpaRepository<RefundProviderAttempt, UUID> {

    Optional<RefundProviderAttempt> findByRefundRequest_Id(UUID refundRequestId);

    Optional<RefundProviderAttempt> findByIdempotencyKey(String idempotencyKey);
}
