package com.manabihub.learning.dto.response;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import com.manabihub.learning.enums.EnrollmentStatus;

public record CourseLearningResponse(
        UUID courseId,
        String courseTitle,
        UUID enrollmentId,
        List<LearningModuleResponse> modules,
        UUID currentLessonBlockId,
        int totalLessons,
        int completedLessons,
        double progressPercent,
        boolean courseCompleted,
        List<String> warnings,
        EnrollmentStatus accessStatus,
        Instant expiresAt
) {
    public CourseLearningResponse(
            UUID courseId,
            String courseTitle,
            UUID enrollmentId,
            List<LearningModuleResponse> modules,
            UUID currentLessonBlockId,
            int totalLessons,
            int completedLessons,
            double progressPercent,
            boolean courseCompleted,
            List<String> warnings
    ) {
        this(courseId, courseTitle, enrollmentId, modules, currentLessonBlockId,
                totalLessons, completedLessons, progressPercent, courseCompleted,
                warnings, null, null);
    }
}
