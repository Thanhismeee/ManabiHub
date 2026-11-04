package com.manabihub.learning.dto.response;

import com.manabihub.course.enums.JlptLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WishlistItemResponse(
        UUID id,
        Instant addedAt,
        UUID courseId,
        String title,
        String slug,
        String thumbnailUrl,
        JlptLevel jlptLevel,
        String category,
        BigDecimal price,
        String currency,
        String teacherName,
        Integer totalLessons
) {
}
