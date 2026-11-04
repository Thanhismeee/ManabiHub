package com.manabihub.learning.service;

import com.manabihub.common.response.PageResponse;
import com.manabihub.learning.dto.response.StudentCourseSummaryResponse;
import com.manabihub.learning.dto.response.StudentDashboardStatsResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StudentLearningService {
    StudentDashboardStatsResponse getDashboardStats(UUID userId);
    PageResponse<StudentCourseSummaryResponse> getEnrolledCourses(UUID userId, Pageable pageable);
}
