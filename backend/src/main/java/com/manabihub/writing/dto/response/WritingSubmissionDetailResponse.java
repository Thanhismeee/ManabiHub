package com.manabihub.writing.dto.response;

import com.manabihub.writing.enums.WritingSubmissionStatus;

import java.time.Instant;
import java.util.UUID;

public record WritingSubmissionDetailResponse(
        UUID id,
        UUID courseId,
        String courseTitle,
        UUID lessonId,
        String lessonTitle,
        String studentName,
        String studentEmail,
        String content,
        WritingSubmissionStatus status,
        Instant submittedAt,
        AiWritingSuggestionResponse aiSuggestion,
        TeacherWritingFeedbackResponse teacherFeedback
) {
}
