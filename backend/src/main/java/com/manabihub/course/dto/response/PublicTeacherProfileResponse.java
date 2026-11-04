package com.manabihub.course.dto.response;

import java.util.List;
import java.util.UUID;

public record PublicTeacherProfileResponse(
        UUID id,
        String displayName,
        String avatarUrl,
        String bio,
        boolean verified,
        long publishedCourseCount,
        List<PublicTeacherCredentialResponse> credentials,
        PublicTeacherRatingSummaryResponse ratingSummary,
        List<PublicTeacherCourseResponse> courses
) {
}
