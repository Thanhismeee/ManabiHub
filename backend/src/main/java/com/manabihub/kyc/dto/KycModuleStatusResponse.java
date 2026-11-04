package com.manabihub.kyc.dto;

import java.time.Instant;

public record KycModuleStatusResponse(
        String status,
        String statusLabel,
        boolean canInteract,
        Instant completedAt,
        String detail
) {
}
