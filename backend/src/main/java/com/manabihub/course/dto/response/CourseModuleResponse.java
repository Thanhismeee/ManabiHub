package com.manabihub.course.dto.response;

import java.util.List;
import java.util.UUID;

public record CourseModuleResponse(
        UUID id,
        String title,
        String description,
        int orderIndex,
        List<LessonBlockResponse> blocks
) {
}
