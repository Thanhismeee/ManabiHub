package com.manabihub.wallet.repository;

import com.manabihub.wallet.entity.PlatformCommissionLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformCommissionLedgerRepository extends JpaRepository<PlatformCommissionLedger, UUID> {
    boolean existsByOrderItem_IdAndEventType(
            UUID orderItemId,
            PlatformCommissionLedger.CommissionEventType eventType);

    long countByOrderItem_Id(UUID orderItemId);
}
