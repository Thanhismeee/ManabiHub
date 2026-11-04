package com.manabihub.course.dto.response;

import java.math.BigDecimal;

public record PublicTeacherRatingSummaryResponse(
        BigDecimal averageRating,
        long reviewCount
) {
}
