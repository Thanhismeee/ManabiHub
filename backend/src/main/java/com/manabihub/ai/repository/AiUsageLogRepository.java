package com.manabihub.ai.repository;

import com.manabihub.ai.entity.AiUsageLog;
import com.manabihub.ai.enums.AiUsageRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, UUID> {
    long countByUserIdAndRequestStatusInAndCreatedAtAfter(UUID userId, java.util.Collection<AiUsageRequestStatus> statuses, Instant after);

    long countByUserIdAndFeatureCodeAndRequestStatusAndCreatedAtAfter(
            UUID userId,
            String featureCode,
            AiUsageRequestStatus requestStatus,
            Instant createdAt
    );
}
