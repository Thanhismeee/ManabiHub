package com.manabihub.review.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.review.dto.request.UpsertCourseReviewRequest;
import com.manabihub.review.dto.response.CourseReviewResponse;
import com.manabihub.review.service.CourseReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/courses/{courseId}/review")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentCourseReviewController {

    private final CourseReviewService courseReviewService;

    @GetMapping
    public ApiResponse<CourseReviewResponse> getMyReview(
            @PathVariable UUID courseId
    ) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Course review loaded.",
                courseReviewService.getMyReview(courseId)
        );
    }

    @PutMapping
    public ApiResponse<CourseReviewResponse> upsertMyReview(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpsertCourseReviewRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.COURSE_REVIEW_SAVED,
                "Course review saved.",
                courseReviewService.upsertMyReview(courseId, request)
        );
    }
}
