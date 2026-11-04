package com.manabihub.order.dto.request;

import com.manabihub.payment.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to create a purchase order for a single published course (UC-08).
 *
 * @param courseId      the course to buy
 * @param paymentMethod how to pay; {@code null} defaults to {@code VNPAY}
 */
public record CreateOrderRequest(
        @NotNull(message = "courseId is required")
        UUID courseId,
        PaymentMethod paymentMethod
) {
}
