package com.manabihub.identity.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentProfileResponse
        extends UserProfileResponse {

    private String displayName;

    private String jlptGoal;

    private java.util.List<StudentLearningGoalResponse> learningGoals = java.util.List.of();
}
