package com.manabihub.identity.dto.response;

import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import lombok.Builder;

import java.util.UUID;

@Builder
public record StudentLearningGoalResponse(
        UUID id,
        LearningLanguage learningLanguage,
        ProficiencyFramework proficiencyFramework,
        String frameworkVersion,
        String proficiencyLevel,
        boolean primaryGoal
) {
}
