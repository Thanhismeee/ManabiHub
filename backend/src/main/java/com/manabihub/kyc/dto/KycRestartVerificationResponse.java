package com.manabihub.kyc.dto;

import java.util.Map;
import java.util.UUID;

public record KycRestartVerificationResponse(
        UUID teacherId,
        String teacherKycStatus,
        boolean canPublishCourse,
        KycRequestResponse request,
        KycModuleStatusResponse identityVerification,
        KycModuleStatusResponse certificateVerification,
        boolean auditLogged,
        Map<String, Object> srsTrace
) {
}
