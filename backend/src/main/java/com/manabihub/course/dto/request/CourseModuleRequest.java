package com.manabihub.course.dto.request;

import jakarta.validation.constraints.Size;

public record CourseModuleRequest(
        @Size(max = 120) String title,
        @Size(max = 1000) String description
) {
}
