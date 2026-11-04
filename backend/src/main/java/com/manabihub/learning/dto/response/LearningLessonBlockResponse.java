package com.manabihub.learning.dto.response;

import com.manabihub.course.dto.response.FlashcardItemResponse;
import com.manabihub.learning.dto.response.StudentQuizQuestionResponse;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.learning.enums.FlashcardStatus;
import com.manabihub.learning.enums.LessonProgressStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LearningLessonBlockResponse(
        UUID id,
        UUID moduleId,
        LessonBlockType type,
        String title,
        String content,
        String videoUrl,
        Integer durationMinutes,
        String quizQuestion,
        List<String> quizOptions,
        List<StudentQuizQuestionResponse> quizItems,
        List<FlashcardItemResponse> flashcards,
        List<FlashcardStatus> flashcardStatuses,
        String writingPrompt,
        String rubric,
        int orderIndex,
        boolean contentAvailable,
        LessonProgressStatus progressStatus,
        Integer lastVideoPositionSeconds,
        Instant completedAt,
        boolean current,
        Integer watchedVideoSeconds,
        boolean locked
) {
    public LearningLessonBlockResponse(
            UUID id,
            UUID moduleId,
            LessonBlockType type,
            String title,
            String content,
            String videoUrl,
            Integer durationMinutes,
            String quizQuestion,
            List<String> quizOptions,
            List<StudentQuizQuestionResponse> quizItems,
            List<FlashcardItemResponse> flashcards,
            List<FlashcardStatus> flashcardStatuses,
            String writingPrompt,
            String rubric,
            int orderIndex,
            boolean contentAvailable,
            LessonProgressStatus progressStatus,
            Integer lastVideoPositionSeconds,
            Instant completedAt,
            boolean current
    ) {
        this(id, moduleId, type, title, content, videoUrl, durationMinutes, quizQuestion,
                quizOptions, quizItems, flashcards, flashcardStatuses, writingPrompt, rubric,
                orderIndex, contentAvailable, progressStatus, lastVideoPositionSeconds,
                completedAt, current, null, false);
    }
}
