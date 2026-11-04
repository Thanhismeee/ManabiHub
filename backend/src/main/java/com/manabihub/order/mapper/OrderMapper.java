package com.manabihub.order.mapper;

import com.manabihub.course.entity.Course;
import com.manabihub.order.dto.response.OrderItemResponse;
import com.manabihub.order.dto.response.OrderResponse;
import com.manabihub.order.entity.Order;
import com.manabihub.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getTotalAmount(),
                order.getWalletAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getType().name(),
                order.getCreatedAt(),
                itemResponses);
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        Course course = item.getCourse();
        return new OrderItemResponse(
                item.getId(),
                course.getId(),
                course.getTitle(),
                course.getThumbnailUrl(),
                item.getPrice());
    }
}
