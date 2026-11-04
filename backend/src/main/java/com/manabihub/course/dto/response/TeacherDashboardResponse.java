package com.manabihub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TeacherDashboardResponse {
    private long totalCourses;
    private long draftOrCorrection;
    private long pendingApproval;
    private long published;
    private List<CourseDraftResponse> recentCourses;
}
