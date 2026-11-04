package com.manabihub.wallet.service;

import com.manabihub.order.entity.Order;
import com.manabihub.wallet.entity.EscrowLedger;

import java.util.List;
import java.util.UUID;

/**
 * Holds a paid order's funds in escrow for the teacher(s) of the purchased course(s).
 */
public interface EscrowService {

    /**
     * Creates a {@code HELD} escrow ledger entry per order item and freezes the
     * corresponding amount in each teacher's wallet. Idempotent: calling it again
     * for an order that already has escrow entries is a no-op and returns the
     * existing entries.
     */
    List<EscrowLedger> holdForOrder(Order order);

    /** Processes the release of a single eligible escrow record. Returns true if released, false if blocked. */
    boolean processEscrowRelease(UUID escrowId);

    /**
     * Reverses teacher and platform allocations while an order is still held.
     * This is the idempotent accounting primitive used by the later refund use
     * cases after the payment provider confirms the refund.
     *
     * @return true when at least one allocation was reversed, false when the
     *         order had already been fully reversed
     */
    boolean reverseHeldAllocationsForRefund(UUID orderId);

    /**
     * Reverses exactly one order item's immutable teacher-net and commission
     * allocation after a provider-confirmed refund.
     */
    boolean reverseHeldAllocationForRefund(UUID orderItemId);

    /**
     * Gets the escrow ledger history for a specific teacher by their user ID.
     */
    org.springframework.data.domain.Page<com.manabihub.wallet.dto.response.EscrowLedgerResponse> getTeacherEscrowLedgerByUserId(UUID userId, org.springframework.data.domain.Pageable pageable);
}
