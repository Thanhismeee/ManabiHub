package com.manabihub.course.dto.response;

import java.util.UUID;

public record PublicTeacherSummaryResponse(
        UUID id,
        String displayName,
        String avatarUrl,
        String bio,
        boolean verified,
        long publishedCourseCount
) {
}
