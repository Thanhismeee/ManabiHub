package com.manabihub.learning.dto.response;

import java.util.UUID;

public record CourseProgressSummaryResponse(
        UUID courseId,
        String courseTitle,
        int totalLessons,
        int completedLessons,
        double progressPercent,
        UUID nextLessonBlockId,
        String nextLessonTitle,
        boolean courseCompleted,
        FinalTestEligibilityResponse finalTestEligibility,
        CertificateEligibilityResponse certificateEligibility
) {
}
