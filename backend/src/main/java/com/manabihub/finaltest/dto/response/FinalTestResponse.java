package com.manabihub.finaltest.dto.response;

import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.finaltest.dto.request.FinalTestQuestionDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalTestResponse {

    private UUID id;
    private UUID courseId;
    private Integer timeLimitMinutes;
    private Integer passingScore;
    private Integer maxRetakes;
    private JlptLevel jlptLevel;
    private LearningLanguage learningLanguage;
    private ProficiencyFramework proficiencyFramework;
    private String frameworkVersion;
    private String proficiencyLevel;
    private String assessmentFormat;
    private String skillFocus;

    @Builder.Default
    private List<FinalTestQuestionDto> questions = new ArrayList<>();
}
