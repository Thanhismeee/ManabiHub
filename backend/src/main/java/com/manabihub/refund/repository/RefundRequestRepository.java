package com.manabihub.refund.repository;

import com.manabihub.refund.entity.RefundRequest;
import com.manabihub.refund.enums.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID>, JpaSpecificationExecutor<RefundRequest> {

    @Query("""
            SELECT r FROM RefundRequest r
            WHERE r.status = 'PENDING' AND r.autoRefundNextAttemptAt <= :now
            ORDER BY r.autoRefundNextAttemptAt, r.id
            """)
    List<RefundRequest> findAutomaticRefundsDue(@Param("now") java.time.Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r WHERE r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") UUID id);

    Page<RefundRequest> findByStatus(RefundStatus status, Pageable pageable);

    Page<RefundRequest> findByStatusIn(
            Collection<RefundStatus> statuses,
            Pageable pageable
    );

    List<RefundRequest> findByOrderItem_IdAndStatusIn(UUID orderItemId, Collection<RefundStatus> statuses);

    Optional<RefundRequest> findFirstByOrderItem_IdAndStatusInOrderByCreatedAtDesc(
            UUID orderItemId,
            Collection<RefundStatus> statuses
    );

    Optional<RefundRequest> findFirstByOrder_IdAndStatusInOrderByCreatedAtDesc(
            UUID orderId,
            Collection<RefundStatus> statuses
    );

    Page<RefundRequest> findByStudent_Id(UUID studentId, Pageable pageable);

    Optional<RefundRequest> findByIdAndStudent_Id(UUID id, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r WHERE r.id = :id AND r.student.id = :studentId")
    Optional<RefundRequest> findByIdAndStudentIdForUpdate(
            @Param("id") UUID id,
            @Param("studentId") UUID studentId
    );
}
