package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStudentProfileRequest
        extends UpdateUserProfileRequest {

    @Size(max = 255)
    private String displayName;

    @Size(max = 20)
    private String jlptGoal;

    /** Null means an older client is updating only the legacy JLPT goal. */
    @Valid
    @Size(max = 12)
    private java.util.List<StudentLearningGoalRequest> learningGoals;
}
