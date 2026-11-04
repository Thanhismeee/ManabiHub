package com.manabihub.course.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CourseBuilderResponse(
        UUID draftId,
        String courseTitle,
        List<CourseModuleResponse> modules,
        List<String> validationWarnings,
        Map<String, Object> srsTrace
) {
}
