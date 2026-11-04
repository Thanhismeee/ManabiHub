package com.manabihub.identity.dto.request;

import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentLearningGoalRequest {
    @NotNull
    private LearningLanguage learningLanguage;

    @NotNull
    private ProficiencyFramework proficiencyFramework;

    @Size(max = 32)
    private String frameworkVersion;

    @NotBlank
    @Size(max = 32)
    private String proficiencyLevel;

    private boolean primaryGoal;
}
