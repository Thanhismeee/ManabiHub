package com.manabihub.payout.repository;

import com.manabihub.payout.entity.PayoutReconciliationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayoutReconciliationLogRepository
        extends JpaRepository<PayoutReconciliationLog, UUID> {

    List<PayoutReconciliationLog> findByWithdrawalRequestIdOrderByCreatedAtDesc(
            UUID withdrawalRequestId,
            Pageable pageable
    );
}
