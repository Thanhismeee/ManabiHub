package com.manabihub.payout.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.payout.dto.request.MockPayoutRequest;
import com.manabihub.payout.dto.response.PayoutDecisionResponse;
import com.manabihub.payout.service.PayoutSettlementService;
import com.manabihub.payout.service.PayoutSimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile({"local", "test"})
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FINANCE_MANAGER')")
public class MockAdminPayoutController {

    private final PayoutSimulationService payoutSimulationService;
    private final PayoutSettlementService payoutSettlementService;

    @PostMapping("/{withdrawalRequestId}/mock-approve")
    public ApiResponse<PayoutDecisionResponse> approveWithMockResult(
            @PathVariable UUID withdrawalRequestId,
            @Valid @RequestBody MockPayoutRequest request
    ) {
        payoutSimulationService.selectScenario(
                withdrawalRequestId, request.getScenario());
        return ApiResponse.success(
                MessageCodes.MSG_ADM_004,
                "Simulated payout result applied",
                payoutSettlementService.approvePayout(withdrawalRequestId));
    }
}
