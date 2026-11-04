package com.manabihub.order.repository;

import com.manabihub.order.entity.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT item FROM OrderItem item JOIN FETCH item.order JOIN FETCH item.course WHERE item.id = :id")
    java.util.Optional<OrderItem> findByIdForRefundUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"course", "course.teacher"})
    List<OrderItem> findByOrder_Id(UUID orderId);

    @EntityGraph(attributePaths = {"order", "course", "course.teacher"})
    List<OrderItem> findByOrder_IdIn(Collection<UUID> orderIds);

    @Query("""
            SELECT COUNT(DISTINCT item.order.student.id)
            FROM OrderItem item
            WHERE item.course.id = :courseId
              AND item.order.status = com.manabihub.order.enums.OrderStatus.PAID
              AND item.price > 0
            """)
    long countPaidPurchasersByCourseId(@Param("courseId") UUID courseId);
}
