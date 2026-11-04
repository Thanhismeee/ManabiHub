package com.manabihub.writing.dto.response;

import com.manabihub.writing.enums.WritingSubmissionStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentWritingSubmissionResponse(
        UUID id,
        UUID lessonBlockId,
        String content,
        WritingSubmissionStatus status,
        Instant submittedAt,
        AiWritingSuggestionResponse aiSuggestion,
        TeacherWritingFeedbackResponse teacherFeedback
) {
}
