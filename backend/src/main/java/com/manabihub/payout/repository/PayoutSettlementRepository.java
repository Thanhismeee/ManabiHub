package com.manabihub.payout.repository;

import com.manabihub.payout.entity.PayoutSettlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PayoutSettlementRepository extends JpaRepository<PayoutSettlement, UUID> {

    Optional<PayoutSettlement> findByWithdrawalRequestId(UUID withdrawalRequestId);

    boolean existsByProviderAndProviderReferenceId(String provider, String providerReferenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM PayoutSettlement ps WHERE ps.id = :id")
    Optional<PayoutSettlement> findByIdWithLock(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM PayoutSettlement ps WHERE ps.withdrawalRequestId = :withdrawalRequestId")
    Optional<PayoutSettlement> findByWithdrawalRequestIdWithLock(
            @Param("withdrawalRequestId") UUID withdrawalRequestId
    );
}
