package com.manabihub.kyc.dto;

import java.util.Map;
import java.util.UUID;

public record KycIdentityVerificationResponse(
        UUID teacherId,
        String teacherKycStatus,
        KycRequestResponse request,
        KycModuleStatusResponse identityVerification,
        KycModuleStatusResponse certificateVerification,
        boolean auditLogged,
        Map<String, Object> srsTrace
) {
}
