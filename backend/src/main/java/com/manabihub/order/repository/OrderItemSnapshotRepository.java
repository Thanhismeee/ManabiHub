package com.manabihub.order.repository;

import com.manabihub.order.entity.OrderItemSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderItemSnapshotRepository extends JpaRepository<OrderItemSnapshot, UUID> {
    Optional<OrderItemSnapshot> findByOrderItem_Id(UUID orderItemId);
    boolean existsByOrderItem_Id(UUID orderItemId);
}
