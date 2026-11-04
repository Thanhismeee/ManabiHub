package com.manabihub.course.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.course.dto.request.CourseReviewRequest;
import com.manabihub.course.dto.response.CourseApprovalDetailResponse;
import com.manabihub.course.dto.response.CourseApprovalQueueResponse;
import com.manabihub.course.service.AdminCourseApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/course-approvals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COURSE_MANAGER')")
public class AdminCourseApprovalController {

    private final AdminCourseApprovalService courseApprovalService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseApprovalQueueResponse>>> getQueue(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        List<CourseApprovalQueueResponse> queue = courseApprovalService.getQueue(adminId);
        return ResponseEntity.ok(ApiResponse.success(queue));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseApprovalDetailResponse>> getDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        CourseApprovalDetailResponse detail = courseApprovalService.getDetail(adminId, id);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<Void>> reviewCourse(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CourseReviewRequest request
    ) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        courseApprovalService.reviewCourse(adminId, id, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
