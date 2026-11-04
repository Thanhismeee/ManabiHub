package com.manabihub.course.dto.request;

import com.manabihub.course.enums.LessonBlockType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LessonBlockRequest(
        LessonBlockType type,
        @Size(max = 80) String title,
        String content,
        String videoUrl,
        Integer durationMinutes,
        String quizQuestion,
        List<@Size(max = 500) String> quizOptions,
        String quizAnswer,
        List<@Valid QuizQuestionRequest> quizItems,
        List<@Valid FlashcardItemRequest> flashcards,
        String writingPrompt,
        String rubric
) {
        public LessonBlockRequest(
                LessonBlockType type,
                String title,
                String content,
                String videoUrl,
                Integer durationMinutes,
                String quizQuestion,
                List<@Size(max = 500) String> quizOptions,
                String quizAnswer,
                List<@Valid FlashcardItemRequest> flashcards,
                String writingPrompt,
                String rubric
        ) {
                this(
                        type,
                        title,
                        content,
                        videoUrl,
                        durationMinutes,
                        quizQuestion,
                        quizOptions,
                        quizAnswer,
                        null,
                        flashcards,
                        writingPrompt,
                        rubric
                );
        }
}
