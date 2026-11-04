package com.manabihub.writing.dto.response;

import java.math.BigDecimal;

public record WritingReviewOverviewResponse(
        long totalSubmissions,
        long pendingSubmissions,
        long reviewedSubmissions,
        BigDecimal averageScore
) {
}
