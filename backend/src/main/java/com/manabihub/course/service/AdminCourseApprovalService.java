package com.manabihub.course.service;

import com.manabihub.course.dto.request.CourseReviewRequest;
import com.manabihub.course.dto.response.CourseApprovalDetailResponse;
import com.manabihub.course.dto.response.CourseApprovalQueueResponse;

import java.util.List;
import java.util.UUID;

public interface AdminCourseApprovalService {
    List<CourseApprovalQueueResponse> getQueue(UUID adminId);
    CourseApprovalDetailResponse getDetail(UUID adminId, UUID courseId);
    void reviewCourse(UUID adminId, UUID courseId, CourseReviewRequest request);
}
