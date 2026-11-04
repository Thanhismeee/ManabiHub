package com.manabihub.course.dto.response;

import com.manabihub.course.enums.LessonBlockType;

import java.util.List;
import java.util.UUID;

public record LessonBlockResponse(
        UUID id,
        LessonBlockType type,
        String title,
        String content,
        String videoUrl,
        Integer durationMinutes,
        String quizQuestion,
        List<String> quizOptions,
        String quizAnswer,
        List<QuizQuestionResponse> quizItems,
        List<FlashcardItemResponse> flashcards,
        String writingPrompt,
        String rubric,
        int orderIndex,
        boolean interactionRequiredAfter,
        boolean interactionSatisfied,
        String validationMessage
) {
}
