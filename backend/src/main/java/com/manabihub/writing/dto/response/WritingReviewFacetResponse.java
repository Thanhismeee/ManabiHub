package com.manabihub.writing.dto.response;

import java.util.List;
import java.util.UUID;

public record WritingReviewFacetResponse(
        List<CourseOption> courses
) {
    public record CourseOption(
            UUID id,
            String title,
            List<LessonOption> lessons
    ) {
    }

    public record LessonOption(
            UUID id,
            String title
    ) {
    }
}
