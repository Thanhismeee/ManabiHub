package com.manabihub.writing.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import com.manabihub.writing.dto.request.TeacherWritingFeedbackRequest;
import com.manabihub.writing.dto.response.WritingSubmissionDetailResponse;
import com.manabihub.writing.dto.response.WritingReviewFacetResponse;
import com.manabihub.writing.dto.response.WritingReviewOverviewResponse;
import com.manabihub.writing.dto.response.WritingSubmissionSummaryResponse;
import com.manabihub.writing.enums.WritingSubmissionStatus;
import com.manabihub.writing.service.TeacherWritingReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher/writing-submissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
@Validated
public class TeacherWritingReviewController {

    private final TeacherWritingReviewService teacherWritingReviewService;

    @GetMapping
    public ApiResponse<PageResponse<WritingSubmissionSummaryResponse>> listSubmissions(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) Boolean reviewed,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID lessonId,
            @RequestParam(required = false) WritingSubmissionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.success(
                teacherWritingReviewService.listSubmissions(
                        query, reviewed, courseId, lessonId, status, PageRequest.of(page, size)
                )
        );
    }

    @GetMapping("/facets")
    public ApiResponse<WritingReviewFacetResponse> getFacets() {
        return ApiResponse.success(teacherWritingReviewService.getFacets());
    }

    @GetMapping("/overview")
    public ApiResponse<WritingReviewOverviewResponse> getOverview(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID lessonId,
            @RequestParam(required = false) WritingSubmissionStatus status
    ) {
        return ApiResponse.success(
                teacherWritingReviewService.getOverview(query, courseId, lessonId, status)
        );
    }

    @GetMapping("/{submissionId}")
    public ApiResponse<WritingSubmissionDetailResponse> getSubmission(
            @PathVariable UUID submissionId
    ) {
        return ApiResponse.success(teacherWritingReviewService.getSubmission(submissionId));
    }

    @PutMapping("/{submissionId}/feedback")
    public ApiResponse<WritingSubmissionDetailResponse> saveFeedback(
            @PathVariable UUID submissionId,
            @Valid @RequestBody TeacherWritingFeedbackRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.TEACHER_FEEDBACK_SUBMITTED,
                "Teacher feedback saved",
                teacherWritingReviewService.saveFeedback(submissionId, request)
        );
    }
}
