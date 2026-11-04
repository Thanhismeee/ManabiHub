package com.manabihub.payout.dto.request;

import com.manabihub.payout.enums.MockPayoutScenario;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MockPayoutRequest {
    @NotNull
    private MockPayoutScenario scenario;
}
