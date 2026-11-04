package com.manabihub.oversight.repository;

import com.manabihub.oversight.entity.OperationalDecisionReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationalDecisionReviewRepository
        extends JpaRepository<OperationalDecisionReview, UUID> {

    Optional<OperationalDecisionReview> findByAuditLog_Id(UUID auditLogId);

    List<OperationalDecisionReview> findAllByAuditLog_IdIn(Collection<UUID> auditLogIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from OperationalDecisionReview review where review.auditLog.id = :auditLogId")
    Optional<OperationalDecisionReview> findByAuditLogIdForUpdate(@Param("auditLogId") UUID auditLogId);
}
