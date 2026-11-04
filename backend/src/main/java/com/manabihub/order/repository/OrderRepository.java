package com.manabihub.order.repository;

import com.manabihub.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
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
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    Page<Order> findByStudent_Id(UUID studentId, Pageable pageable);

    Page<Order> findByStudent_IdAndStatus(UUID studentId,
                                          com.manabihub.order.enums.OrderStatus status,
                                          Pageable pageable);

    List<Order> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            com.manabihub.order.enums.OrderStatus status,
            Instant createdAt
    );

    /**
     * Loads an order by its code holding a pessimistic write lock.
     * Used by the payment webhook handler to serialize concurrent IPN callbacks
     * for the same order (idempotency guard).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeForUpdate(@Param("orderCode") String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") UUID orderId);

    /**
     * Ids of the student's own orders whose code contains {@code code} (case-insensitive).
     * Backs the reference-code search on the wallet transaction history (UC-17 step 6).
     */
    @Query("""
            SELECT o.id FROM Order o
            WHERE o.student.id = :studentId
              AND UPPER(o.orderCode) LIKE UPPER(CONCAT('%', :code, '%'))
            """)
    java.util.List<UUID> findIdsByStudentIdAndOrderCodeLike(@Param("studentId") UUID studentId,
                                                            @Param("code") String code);
}
