package com.manabihub.kyc.dto;

import java.util.Map;
import java.util.UUID;

public record KycSubmissionResponse(
        UUID teacherId,
        String teacherKycStatus,
        boolean canPublishCourse,
        KycRequestResponse request,
        boolean adminNotificationCreated,
        boolean auditLogged,
        Map<String, Object> srsTrace
) {
}
