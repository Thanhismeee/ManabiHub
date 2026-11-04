package com.manabihub.learning.dto.response;

import java.util.List;
import java.util.UUID;

public record LearningModuleResponse(
        UUID id,
        String title,
        int orderIndex,
        List<LearningLessonBlockResponse> blocks
) {
}
