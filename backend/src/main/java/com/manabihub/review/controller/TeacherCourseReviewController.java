package com.manabihub.review.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.review.dto.request.TeacherCourseReviewReplyRequest;
import com.manabihub.review.dto.response.CourseReviewResponse;
import com.manabihub.review.service.CourseReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher/course-reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherCourseReviewController {

    private final CourseReviewService courseReviewService;

    @PutMapping("/{reviewId}/reply")
    public ApiResponse<CourseReviewResponse> replyToReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody TeacherCourseReviewReplyRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Đã lưu phản hồi của giảng viên.",
                courseReviewService.replyToReview(reviewId, request)
        );
    }
}
