package com.manabihub.course.dto.response;

import java.util.UUID;

public record CourseCategoryResponse(
        UUID id,
        String code,
        String name,
        String description
) {
}
