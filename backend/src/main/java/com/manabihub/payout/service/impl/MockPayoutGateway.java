package com.manabihub.payout.service.impl;

import com.manabihub.payout.service.PayoutGateway;
import com.manabihub.payout.service.PayoutSimulationService;
import com.manabihub.payout.enums.MockPayoutScenario;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Profile({"local", "test"})
public class MockPayoutGateway implements PayoutGateway, PayoutSimulationService {

    private final ConcurrentMap<String, PayoutGatewayResult> resultsByIdempotencyKey =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MockPayoutScenario> scenariosByIdempotencyKey =
            new ConcurrentHashMap<>();

    @Override
    public void selectScenario(UUID withdrawalRequestId, MockPayoutScenario scenario) {
        String key = idempotencyKey(withdrawalRequestId);
        scenariosByIdempotencyKey.put(key, scenario);
        resultsByIdempotencyKey.remove(key);
    }

    @Override
    public String providerName() {
        return "MOCK_PAYOUT";
    }

    @Override
    public PayoutGatewayResult transfer(PayoutGatewayCommand command) {
        return resultsByIdempotencyKey.computeIfAbsent(
                command.getIdempotencyKey(),
                key -> resultFor(
                        key,
                        scenariosByIdempotencyKey.getOrDefault(
                                key, MockPayoutScenario.SUCCESS))
        );
    }

    @Override
    public PayoutGatewayResult getTransferStatus(String providerReference) {
        return resultsByIdempotencyKey.values().stream()
                .filter(result -> providerReference.equals(result.getProviderReference()))
                .findFirst()
                .orElseGet(() -> PayoutGatewayResult.builder()
                        .success(false)
                        .errorCode("PAYOUT_REFERENCE_NOT_FOUND")
                        .errorMessage("The payout provider reference was not found.")
                        .isRetryable(false)
                        .build());
    }

    private String referenceFor(String idempotencyKey) {
        UUID stableId = UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        return "MOCK-" + stableId.toString().substring(0, 12).toUpperCase();
    }

    private String idempotencyKey(UUID withdrawalRequestId) {
        return "payout-" + withdrawalRequestId;
    }

    private PayoutGatewayResult resultFor(
            String idempotencyKey,
            MockPayoutScenario scenario
    ) {
        return switch (scenario) {
            case SUCCESS -> PayoutGatewayResult.builder()
                    .success(true)
                    .providerReference(referenceFor(idempotencyKey))
                    .build();
            case RETRYABLE_FAILURE -> PayoutGatewayResult.builder()
                    .success(false)
                    .errorCode("MOCK_PAYOUT_TEMPORARY_FAILURE")
                    .errorMessage("Simulated temporary payout provider failure.")
                    .isRetryable(true)
                    .build();
            case PERMANENT_FAILURE -> PayoutGatewayResult.builder()
                    .success(false)
                    .errorCode("MOCK_PAYOUT_PERMANENT_FAILURE")
                    .errorMessage("Simulated permanent payout provider failure.")
                    .isRetryable(false)
                    .build();
        };
    }
}
