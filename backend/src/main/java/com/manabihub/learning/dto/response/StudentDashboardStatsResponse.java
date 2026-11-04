package com.manabihub.learning.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentDashboardStatsResponse {
    private int totalEnrolledCourses;
    private int activeCourses;
    private int completedCourses;
}
