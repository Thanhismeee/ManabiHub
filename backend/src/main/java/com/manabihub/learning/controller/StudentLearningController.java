package com.manabihub.learning.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import com.manabihub.writing.dto.request.WritingSubmissionRequest;
import com.manabihub.writing.dto.request.WritingDraftRequest;
import com.manabihub.writing.dto.response.StudentWritingSubmissionResponse;
import com.manabihub.learning.dto.request.ReviewFlashcardRequest;
import com.manabihub.learning.dto.request.SaveVideoProgressRequest;
import com.manabihub.learning.dto.response.CourseLearningResponse;
import com.manabihub.learning.dto.response.CourseProgressSummaryResponse;
import com.manabihub.learning.dto.response.LessonProgressResponse;
import com.manabihub.learning.dto.response.StudentCourseSummaryResponse;
import com.manabihub.learning.dto.response.StudentDashboardStatsResponse;
import com.manabihub.learning.service.LearningService;
import com.manabihub.learning.service.StudentLearningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentLearningController {

    private final StudentLearningService studentLearningService;
    private final LearningService learningService;

    @GetMapping("/dashboard/stats")
    public ApiResponse<StudentDashboardStatsResponse> getDashboardStats(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Student dashboard statistics retrieved successfully",
                studentLearningService.getDashboardStats(userId));
    }

    @GetMapping("/courses")
    public ApiResponse<PageResponse<StudentCourseSummaryResponse>> getEnrolledCourses(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 12, sort = "enrolledAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Enrolled courses retrieved successfully",
                studentLearningService.getEnrolledCourses(userId, pageable));
    }

    // ── UC-10: study course lessons ─────────────────────────────────────────

    @GetMapping("/courses/{courseId}/learn")
    public ApiResponse<CourseLearningResponse> openOrResumeCourse(@PathVariable UUID courseId) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Course learning content loaded.",
                learningService.openOrResumeCourse(courseId));
    }

    @PutMapping("/lessons/{lessonBlockId}/video-progress")
    public ApiResponse<LessonProgressResponse> saveVideoProgress(
            @PathVariable UUID lessonBlockId,
            @Valid @RequestBody SaveVideoProgressRequest request) {
        return ApiResponse.success(
                MessageCodes.LEARNING_PROGRESS_UPDATED,
                "Video progress saved.",
                learningService.saveVideoProgress(lessonBlockId, request));
    }

    @PutMapping("/lessons/{lessonBlockId}/flashcards/review")
    public ApiResponse<LessonProgressResponse> reviewFlashcard(
            @PathVariable UUID lessonBlockId,
            @Valid @RequestBody ReviewFlashcardRequest request) {
        return ApiResponse.success(
                MessageCodes.LEARNING_PROGRESS_UPDATED,
                "Flashcard review saved.",
                learningService.reviewFlashcard(lessonBlockId, request));
    }

    @PostMapping("/lessons/{lessonBlockId}/complete")
    public ApiResponse<LessonProgressResponse> markLessonComplete(@PathVariable UUID lessonBlockId) {
        return ApiResponse.success(
                MessageCodes.LEARNING_LESSON_COMPLETED,
                "Lesson marked as completed.",
                learningService.markLessonComplete(lessonBlockId));
    }

    // ── UC-14: Writing Assignments ─────────────────────────────────────────

    @GetMapping("/lessons/{lessonBlockId}/writing-submissions/me")
    public ApiResponse<StudentWritingSubmissionResponse> getWritingSubmission(@PathVariable UUID lessonBlockId) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Writing submission retrieved successfully",
                learningService.getWritingSubmission(lessonBlockId));
    }

    @PutMapping("/lessons/{lessonBlockId}/writing-submissions/draft")
    public ApiResponse<StudentWritingSubmissionResponse> saveWritingDraft(
            @PathVariable UUID lessonBlockId,
            @Valid @RequestBody WritingDraftRequest request) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Writing draft saved successfully",
                learningService.saveWritingDraft(lessonBlockId, request));
    }

    @PostMapping("/lessons/{lessonBlockId}/writing-submissions")
    public ApiResponse<StudentWritingSubmissionResponse> submitWriting(
            @PathVariable UUID lessonBlockId,
            @Valid @RequestBody WritingSubmissionRequest request) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Writing assignment submitted successfully",
                learningService.submitWriting(lessonBlockId, request));
    }

    @PostMapping("/lessons/{lessonBlockId}/writing-submissions/{submissionId}/ai-assistance")
    public ApiResponse<StudentWritingSubmissionResponse> requestAiWritingAssistance(
            @PathVariable UUID lessonBlockId,
            @PathVariable UUID submissionId) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "AI writing assistance requested successfully",
                learningService.requestAiWritingAssistance(lessonBlockId, submissionId));
    }

    @GetMapping("/courses/{courseId}/progress")
    public ApiResponse<CourseProgressSummaryResponse> getCourseProgress(@PathVariable UUID courseId) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Course progress loaded.",
                learningService.getCourseProgress(courseId));
    }
}
