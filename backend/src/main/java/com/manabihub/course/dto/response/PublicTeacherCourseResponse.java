package com.manabihub.course.dto.response;

import com.manabihub.course.enums.JlptLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PublicTeacherCourseResponse(
        UUID id,
        String title,
        String slug,
        String thumbnailUrl,
        JlptLevel jlptLevel,
        String category,
        BigDecimal price,
        String currency,
        int totalLessons,
        Instant publishedAt,
        BigDecimal averageRating,
        long reviewCount
) {
}
