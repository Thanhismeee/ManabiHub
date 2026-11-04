package com.manabihub.payout.service;

import com.manabihub.payout.enums.MockPayoutScenario;

import java.util.UUID;

/** Local/test-only control surface for deterministic payout scenarios. */
public interface PayoutSimulationService {
    void selectScenario(UUID withdrawalRequestId, MockPayoutScenario scenario);
}
