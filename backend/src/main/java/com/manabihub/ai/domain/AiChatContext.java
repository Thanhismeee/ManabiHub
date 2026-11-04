package com.manabihub.ai.domain;

import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;

import java.util.UUID;

/**
 * Context intentionally contains one lesson block and course metadata only.
 * It must never be populated from another course or lesson block.
 */
public record AiChatContext(
        UUID courseId,
        UUID lessonBlockId,
        LearningLanguage learningLanguage,
        ProficiencyFramework proficiencyFramework,
        String frameworkVersion,
        String proficiencyLevel,
        String courseTitle,
        String courseDescription,
        String courseOutcomes,
        String lessonTitle,
        String lessonContent
) {
    /** Compatibility constructor for older tests and callers. */
    public AiChatContext(
            UUID courseId,
            UUID lessonBlockId,
            String courseTitle,
            String courseDescription,
            String courseOutcomes,
            String lessonTitle,
            String lessonContent
    ) {
        this(
                courseId,
                lessonBlockId,
                LearningLanguage.JAPANESE,
                ProficiencyFramework.JLPT,
                "CURRENT",
                null,
                courseTitle,
                courseDescription,
                courseOutcomes,
                lessonTitle,
                lessonContent
        );
    }
}
