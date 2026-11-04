package com.manabihub.kyc.dto;

import java.util.Map;
import java.util.UUID;

public record KycStatusResponse(
        UUID teacherId,
        UUID userId,
        String teacherKycStatus,
        String teacherKycStatusLabel,
        boolean canPublishCourse,
        KycModuleStatusResponse identityVerification,
        KycModuleStatusResponse certificateVerification,
        KycRequestResponse latestRequest,
        Map<String, Object> srsTrace
) {
}
