package com.manabihub.payout.service.impl;

import com.manabihub.payout.enums.MockPayoutScenario;
import com.manabihub.payout.service.PayoutGateway;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPayoutGatewayTest {

    private final MockPayoutGateway gateway = new MockPayoutGateway();

    @Test
    void defaultsToSuccessAndKeepsTheIdempotentResult() {
        UUID withdrawalId = UUID.randomUUID();
        PayoutGateway.PayoutGatewayCommand command = command(withdrawalId);

        PayoutGateway.PayoutGatewayResult first = gateway.transfer(command);
        PayoutGateway.PayoutGatewayResult repeated = gateway.transfer(command);

        assertTrue(first.isSuccess());
        assertEquals(first.getProviderReference(), repeated.getProviderReference());
    }

    @Test
    void returnsTheSelectedRetryableFailure() {
        UUID withdrawalId = UUID.randomUUID();
        gateway.selectScenario(withdrawalId, MockPayoutScenario.RETRYABLE_FAILURE);

        PayoutGateway.PayoutGatewayResult result = gateway.transfer(command(withdrawalId));

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertEquals("MOCK_PAYOUT_TEMPORARY_FAILURE", result.getErrorCode());
    }

    @Test
    void returnsTheSelectedPermanentFailure() {
        UUID withdrawalId = UUID.randomUUID();
        gateway.selectScenario(withdrawalId, MockPayoutScenario.PERMANENT_FAILURE);

        PayoutGateway.PayoutGatewayResult result = gateway.transfer(command(withdrawalId));

        assertFalse(result.isSuccess());
        assertFalse(result.isRetryable());
        assertEquals("MOCK_PAYOUT_PERMANENT_FAILURE", result.getErrorCode());
    }

    private PayoutGateway.PayoutGatewayCommand command(UUID withdrawalId) {
        return PayoutGateway.PayoutGatewayCommand.builder()
                .idempotencyKey("payout-" + withdrawalId)
                .build();
    }
}
