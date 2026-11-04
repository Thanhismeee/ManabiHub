package com.manabihub.course.dto.response;

import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CourseDraftResponse(
        UUID id,
        UUID teacherId,
        String title,
        String slug,
        String introduction,
        JlptLevel jlptLevel,
        LearningLanguage learningLanguage,
        ProficiencyFramework proficiencyFramework,
        String frameworkVersion,
        String proficiencyLevel,
        String category,
        String thumbnailUrl,
        String outcomes,
        BigDecimal price,
        String currency,
        String prerequisites,
        String targetStudents,
        CourseStatus status,
        List<String> learningGoals,
        Integer accessDurationDays,
        Instant accessExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> srsTrace
) {
}
