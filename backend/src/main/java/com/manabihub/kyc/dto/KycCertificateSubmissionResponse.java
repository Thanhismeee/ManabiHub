package com.manabihub.kyc.dto;

import java.util.Map;
import java.util.UUID;

public record KycCertificateSubmissionResponse(
        UUID teacherId,
        String teacherKycStatus,
        boolean canPublishCourse,
        KycRequestResponse request,
        KycModuleStatusResponse identityVerification,
        KycModuleStatusResponse certificateVerification,
        boolean adminNotificationCreated,
        boolean auditLogged,
        boolean teacherWorkspaceAvailable,
        String reviewEta,
        String sessionToken,
        Map<String, Object> srsTrace
) {
}
