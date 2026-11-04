package com.manabihub.payment.controller;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.order.entity.Order;
import com.manabihub.order.repository.OrderRepository;
import com.manabihub.payment.config.VnPayProperties;
import com.manabihub.payment.dto.IpnAckResponse;
import com.manabihub.payment.gateway.PaymentGateway;
import com.manabihub.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

/**
 * Payment provider callbacks (UC-08). These endpoints are public — they are called by
 * VNPay (or the local dev simulator), not by an authenticated browser session — and are
 * secured by the VNPay HMAC checksum instead of a JWT.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final OrderRepository orderRepository;
    private final VnPayProperties vnPayProperties;

    /**
     * VNPay IPN (Instant Payment Notification) — the authoritative, server-to-server
     * payment confirmation. Returns the acknowledgement VNPay expects.
     */
    @GetMapping("/vnpay/ipn")
    public IpnAckResponse handleVnPayIpn(@RequestParam Map<String, String> params) {
        return paymentService.handleIpn(params);
    }

    /**
     * VNPay browser return forwarded by the authenticated student frontend. The
     * service verifies the VNPay signature, amount and provider status before applying
     * the same idempotent confirmation transaction as the server-to-server IPN.
     */
    @GetMapping("/vnpay/confirm-return")
    @PreAuthorize("hasRole('STUDENT')")
    public IpnAckResponse handleVnPayReturn(@RequestParam Map<String, String> params) {
        return paymentService.handleVnPayReturn(params);
    }

    /**
     * Local dev simulator: builds a correctly-signed IPN payload for an order and runs it
     * through the exact same {@link PaymentService#handleIpn} path VNPay would trigger, so
     * the flow can be tested end-to-end without exposing localhost to VNPay via a tunnel.
     * Disabled when {@code manabihub.payment.vnpay.dev-simulator-enabled=false}.
     */
    @PostMapping("/dev/ipn")
    public IpnAckResponse simulateIpn(@RequestParam String orderCode,
                                      @RequestParam(defaultValue = "true") boolean success) {
        if (!vnPayProperties.isDevSimulatorEnabled()) {
            throw new BusinessException(
                    MessageCodes.AUTH_FORBIDDEN,
                    "Payment dev simulator is disabled",
                    HttpStatus.FORBIDDEN);
        }

        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.ORDER_NOT_FOUND,
                        "Order was not found",
                        HttpStatus.NOT_FOUND));

        log.info("Simulating VNPay IPN for order {} (success={})", orderCode, success);
        Map<String, String> signedParams = paymentGateway.buildSignedCallbackParams(order, success);
        return paymentService.handleIpn(signedParams);
    }
}
