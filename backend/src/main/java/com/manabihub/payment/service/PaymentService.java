package com.manabihub.payment.service;

import com.manabihub.order.entity.Order;
import com.manabihub.payment.dto.IpnAckResponse;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {

    /**
     * Records a PENDING payment transaction for the order and returns the provider
     * payment URL the browser should be redirected to.
     */
    String initiatePayment(Order order, String clientIp);

    /**
     * Pays a course order instantly from the student's wallet balance — debits the wallet,
     * records a WALLET payment transaction, marks the order paid and fulfils it (enrollment +
     * escrow), all in one transaction and with no payment gateway. The success
     * notification is delivered only after that transaction commits.
     *
     * @throws com.manabihub.common.exception.BusinessException with
     *         {@code WALLET_INSUFFICIENT_BALANCE} if the wallet balance is not enough
     */
    Order payWithWallet(UUID orderId);

    /** Cancels a pending order, marks pending payment components failed and releases wallet reservations. */
    void cancelPendingOrder(UUID orderId);

    /**
     * Combined payment: uses as much of the student's wallet balance as available and charges
     * the remainder via VNPay. Sets the order's wallet portion and returns the VNPay payment
     * URL — or {@code null} if the wallet fully covered the order (already paid).
     */
    String initiateCombinedPayment(Order order, String clientIp);

    /**
     * Processes a provider webhook (VNPay IPN). It verifies the checksum, and on a valid
     * successful callback creates the
     * enrollment, marks the payment/order paid, holds funds in escrow, and notifies the
     * student — all in one transaction. Idempotent: a replayed callback for an
     * already-paid order is a no-op.
     *
     * @return the acknowledgement to send back to the provider
     */
    IpnAckResponse handleIpn(Map<String, String> params);

    /**
     * Processes the browser return from VNPay. The backend verifies the HMAC, amount and
     * provider status before applying the same idempotent confirmation transaction as the
     * IPN. This provides a sandbox/demo fallback when VNPay cannot reach the IPN endpoint.
     */
    IpnAckResponse handleVnPayReturn(Map<String, String> params);

    /** Expires pending VNPay payments and releases any reserved wallet amount. */
    void expirePendingPayments();
}
