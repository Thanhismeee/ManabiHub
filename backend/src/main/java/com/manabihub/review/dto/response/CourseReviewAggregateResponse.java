package com.manabihub.review.dto.response;

import java.math.BigDecimal;

public record CourseReviewAggregateResponse(
        BigDecimal averageRating,
        long reviewCount
) {
    public static CourseReviewAggregateResponse empty() {
        return new CourseReviewAggregateResponse(null, 0);
    }
}
