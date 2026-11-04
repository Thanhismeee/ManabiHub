package com.manabihub.order.service;

import com.manabihub.common.response.PageResponse;
import com.manabihub.order.dto.response.OrderResponse;
import com.manabihub.order.entity.Order;
import com.manabihub.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    /**
     * Creates a PENDING order (with one order item) for the given published course,
     * on behalf of the currently authenticated student.
     *
     * @throws com.manabihub.common.exception.BusinessException if the course is not
     *         published or the student already owns it
     */
    Order createOrder(UUID courseId);

    /**
     * Legacy compatibility only. No student-facing route creates new wallet top-up orders.
     */
    @Deprecated
    Order createTopUpOrder(java.math.BigDecimal amount);

    /**
     * Completes a free (zero-amount) order by enrolling the student immediately and marking
     * the order paid — no payment provider or escrow is involved. Idempotent on enrollment.
     */
    void enrollFreeOrder(Order order);

    /**
     * Returns an order owned by the current student, used by the frontend to poll
     * payment status after redirecting back from the payment provider.
     */
    OrderResponse getOrderForCurrentStudent(UUID orderId);

    /**
     * Returns the current student's purchase history. An optional status filter is
     * applied server-side so pagination remains correct.
     */
    PageResponse<OrderResponse> getOrdersForCurrentStudent(OrderStatus status, Pageable pageable);
}
