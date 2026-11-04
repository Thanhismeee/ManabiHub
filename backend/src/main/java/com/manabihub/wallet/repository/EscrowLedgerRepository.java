package com.manabihub.wallet.repository;

import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.enums.EscrowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscrowLedgerRepository extends JpaRepository<EscrowLedger, UUID> {

    @Query("SELECT e.order.id FROM EscrowLedger e WHERE e.id = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    @Query(value = """
            SELECT NOT EXISTS (
                SELECT 1 FROM payment_transactions p
                WHERE p.order_id = :orderId AND p.succeeded_at IS NOT NULL
            ) OR EXISTS (
                SELECT 1 FROM payment_transactions p
                WHERE p.order_id = :orderId AND p.succeeded_at >= :cutoff
            )
            """, nativeQuery = true)
    boolean isRefundWindowOpen(@Param("orderId") UUID orderId, @Param("cutoff") Instant cutoff);

    interface TeacherCourseRevenueProjection {
        UUID getCourseId();
        String getCourseTitle();
        Long getPurchaseCount();
        Long getRefundedCount();
        java.math.BigDecimal getGrossRevenue();
        java.math.BigDecimal getTeacherNetRevenue();
        java.math.BigDecimal getHeldAmount();
        java.math.BigDecimal getReleasedAmount();
        java.math.BigDecimal getRefundedAmount();
    }

    /** Idempotency guard: an order must never produce more than one escrow hold. */
    boolean existsByOrder_Id(UUID orderId);

    List<EscrowLedger> findByOrder_Id(UUID orderId);

    @Query(value = "SELECT e FROM EscrowLedger e JOIN FETCH e.orderItem oi JOIN FETCH e.course WHERE e.teacher.id = :teacherId",
           countQuery = "SELECT count(e) FROM EscrowLedger e WHERE e.teacher.id = :teacherId")
    org.springframework.data.domain.Page<EscrowLedger> findByTeacher_Id(@Param("teacherId") UUID teacherId, Pageable pageable);

    Optional<EscrowLedger> findByOrderItem_Id(UUID orderItemId);

    /**
     * Ids of the teacher's own escrow entries whose order code contains {@code code}
     * (case-insensitive). Backs the reference-code search on the teacher wallet history (UC-17).
     */
    @Query("""
            SELECT e.id FROM EscrowLedger e
            WHERE e.teacher.id = :teacherId
              AND UPPER(e.order.orderCode) LIKE UPPER(CONCAT('%', :code, '%'))
            """)
    List<UUID> findIdsByTeacherIdAndOrderCodeLike(@Param("teacherId") UUID teacherId,
                                                  @Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowLedger e WHERE e.id = :id")
    Optional<EscrowLedger> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowLedger e WHERE e.order.id = :orderId ORDER BY e.id ASC")
    List<EscrowLedger> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowLedger e WHERE e.orderItem.id = :orderItemId")
    Optional<EscrowLedger> findByOrderItemIdForUpdate(@Param("orderItemId") UUID orderItemId);

    @Query("SELECT e FROM EscrowLedger e WHERE e.status = :status AND e.releaseAt <= :releaseAt " +
           "AND (e.createdAt > :lastCreatedAt OR (e.createdAt = :lastCreatedAt AND e.id > :lastId)) " +
           "ORDER BY e.createdAt ASC, e.id ASC")
    List<EscrowLedger> findNextEligibleChunk(
            @Param("status") EscrowStatus status,
            @Param("releaseAt") Instant releaseAt,
            @Param("lastCreatedAt") Instant lastCreatedAt,
            @Param("lastId") UUID lastId,
            Pageable pageable);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM refund_requests request
                WHERE request.order_id = :orderId
                  AND request.status IN (
                      'PENDING',
                      'PROCESSING',
                      'RECONCILIATION_REQUIRED',
                      'APPROVED'
                  )
            )
            """, nativeQuery = true)
    boolean existsBlockingRefundRequest(@Param("orderId") UUID orderId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM violation_reports report
                WHERE report.status = 'PENDING'
                  AND (
                    (report.target_type = 'COURSE' AND report.target_id = :courseId)
                    OR
                    (report.target_type = 'USER' AND report.target_id = :teacherUserId)
                  )
            )
            """, nativeQuery = true)
    boolean existsPendingTrustCase(
            @Param("courseId") UUID courseId,
            @Param("teacherUserId") UUID teacherUserId);

    @Query("""
            select coalesce(sum(escrow.amount), 0)
            from EscrowLedger escrow
            where escrow.teacher.id = :teacherId
              and escrow.status = :status
            """)
    java.math.BigDecimal sumAmountByTeacherIdAndStatus(
            @Param("teacherId") UUID teacherId,
            @Param("status") EscrowStatus status
    );

    @Query("""
            select coalesce(sum(escrow.amount), 0)
            from EscrowLedger escrow
            where escrow.course.id = :courseId
              and escrow.status != 'REFUNDED'
              and escrow.createdAt >= :startDate
              and escrow.createdAt <= :endDate
            """)
    java.math.BigDecimal sumNetRevenueByCourseIdAndDateRange(
            @Param("courseId") UUID courseId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("""
            select coalesce(sum(oi.price), 0)
            from EscrowLedger escrow
            join escrow.orderItem oi
            where escrow.course.id = :courseId
              and escrow.status != 'REFUNDED'
              and escrow.createdAt >= :startDate
              and escrow.createdAt <= :endDate
            """)
    java.math.BigDecimal sumGrossRevenueByCourseIdAndDateRange(
            @Param("courseId") UUID courseId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Aggregated teacher revenue by course.  The ledger is the source of truth for
     * teacher net revenue: HELD/FROZEN is still in platform escrow, RELEASED has
     * settled into the teacher wallet, and REFUNDED is no longer payable.
     */
    @Query("""
            select escrow.course.id as courseId,
                   escrow.course.title as courseTitle,
                   count(escrow.id) as purchaseCount,
                   coalesce(sum(case when escrow.status = 'REFUNDED' then 1 else 0 end), 0) as refundedCount,
                   coalesce(sum(case when escrow.status <> 'REFUNDED' then oi.price else 0 end), 0) as grossRevenue,
                   coalesce(sum(case when escrow.status <> 'REFUNDED' then escrow.amount else 0 end), 0) as teacherNetRevenue,
                   coalesce(sum(case when escrow.status in ('HELD', 'FROZEN') then escrow.amount else 0 end), 0) as heldAmount,
                   coalesce(sum(case when escrow.status = 'RELEASED' then escrow.amount else 0 end), 0) as releasedAmount,
                   coalesce(sum(case when escrow.status = 'REFUNDED' then escrow.amount else 0 end), 0) as refundedAmount
            from EscrowLedger escrow
            join escrow.orderItem oi
            where escrow.teacher.id = :teacherId
            group by escrow.course.id, escrow.course.title
            order by escrow.course.title asc
            """)
    List<TeacherCourseRevenueProjection> summarizeTeacherRevenueByCourse(
            @Param("teacherId") UUID teacherId);
}
