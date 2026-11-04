package com.manabihub.wallet.service;

import com.manabihub.order.entity.Order;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.repository.OrderRepository;
import com.manabihub.payment.entity.PaymentTransaction;
import com.manabihub.payment.enums.PaymentStatus;
import com.manabihub.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletReservationExpiryServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private StudentWalletService studentWalletService;

    private WalletReservationExpiryService service;

    @BeforeEach
    void setUp() {
        service = new WalletReservationExpiryService(
                orderRepository,
                paymentTransactionRepository,
                studentWalletService);
    }

    @Test
    void expirePendingOrder_releasesReservationAndClosesPendingPaymentComponents() {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        Order order = Order.builder().id(orderId).status(OrderStatus.PENDING).build();
        PaymentTransaction wallet = PaymentTransaction.builder()
                .provider("WALLET").status(PaymentStatus.PENDING).build();
        PaymentTransaction gateway = PaymentTransaction.builder()
                .provider("VNPAY").status(PaymentStatus.PENDING).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findByOrder_IdAndStatusInOrderByCreatedAtAsc(
                orderId, List.of(PaymentStatus.PENDING)))
                .thenReturn(List.of(wallet, gateway));

        service.expire(orderId, now);

        verify(studentWalletService).releaseForOrder(orderId, now);
        assertEquals(PaymentStatus.FAILED, wallet.getStatus());
        assertEquals(PaymentStatus.FAILED, gateway.getStatus());
        assertEquals(OrderStatus.FAILED, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void expirePaidOrder_hasNoFinancialSideEffects() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).status(OrderStatus.PAID).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        service.expire(orderId, Instant.now());

        verify(studentWalletService, never()).releaseForOrder(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(paymentTransactionRepository, never())
                .findByOrder_IdAndStatusInOrderByCreatedAtAsc(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(orderRepository, never()).save(order);
    }
}
