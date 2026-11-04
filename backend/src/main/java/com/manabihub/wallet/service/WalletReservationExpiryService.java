package com.manabihub.wallet.service;

import com.manabihub.order.entity.Order;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.repository.OrderRepository;
import com.manabihub.payment.enums.PaymentStatus;
import com.manabihub.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletReservationExpiryService {

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StudentWalletService studentWalletService;

    @Transactional
    public void expire(UUID orderId, Instant now) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        studentWalletService.releaseForOrder(orderId, now);
        paymentTransactionRepository
                .findByOrder_IdAndStatusInOrderByCreatedAtAsc(
                        orderId,
                        List.of(PaymentStatus.PENDING))
                .forEach(payment -> payment.setStatus(PaymentStatus.FAILED));
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
    }
}
