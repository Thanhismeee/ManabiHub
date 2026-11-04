package com.manabihub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TeacherCourseAnalyticsResponse {
    private long totalEnrollment;
    private long activeLearners;
    private long completedLearners;
    private double completionRate;
    private BigDecimal grossRevenue;
    private BigDecimal netRevenue;
    private double refundRate;
    private BigDecimal averageRating;
    private long totalReviews;
}
