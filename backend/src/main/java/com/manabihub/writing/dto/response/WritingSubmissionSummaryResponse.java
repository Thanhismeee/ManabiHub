package com.manabihub.writing.dto.response;

import com.manabihub.writing.enums.WritingSubmissionStatus;

import java.time.Instant;
import java.util.UUID;

public record WritingSubmissionSummaryResponse(
        UUID id,
        UUID courseId,
        String courseTitle,
        UUID lessonId,
        String lessonTitle,
        String studentName,
        String studentEmail,
        WritingSubmissionStatus status,
        Instant submittedAt,
        boolean hasAiSuggestion,
        boolean hasTeacherFeedback,
        java.math.BigDecimal score
) {
}
