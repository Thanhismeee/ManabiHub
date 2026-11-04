package com.manabihub.learning.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.learning.dto.request.FinalTestSubmissionRequest;
import com.manabihub.learning.dto.request.QuizSubmissionRequest;
import com.manabihub.learning.dto.response.FinalTestEligibilityResponse;
import com.manabihub.learning.dto.response.FinalTestStartResponse;
import com.manabihub.learning.dto.response.FinalTestSubmissionResponse;
import com.manabihub.learning.dto.response.QuizSubmissionResponse;
import com.manabihub.learning.service.StudentAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentAssessmentController {

    private final StudentAssessmentService assessmentService;

    @PostMapping("/lessons/{lessonBlockId}/quiz-submissions")
    public ApiResponse<QuizSubmissionResponse> submitQuiz(
            @PathVariable UUID lessonBlockId,
            @Valid @RequestBody QuizSubmissionRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.LEARNING_QUIZ_SUBMITTED,
                "Quiz submitted successfully.",
                assessmentService.submitQuiz(lessonBlockId, request)
        );
    }

    @GetMapping("/courses/{courseId}/final-test/eligibility")
    public ApiResponse<FinalTestEligibilityResponse> getFinalTestEligibility(@PathVariable UUID courseId) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Final Test eligibility loaded.",
                assessmentService.getFinalTestEligibility(courseId)
        );
    }

    @PostMapping("/courses/{courseId}/final-test/attempts")
    public ApiResponse<FinalTestStartResponse> startFinalTest(@PathVariable UUID courseId) {
        return ApiResponse.success(
                MessageCodes.LEARNING_FINAL_TEST_STARTED,
                "Final Test started.",
                assessmentService.startFinalTest(courseId)
        );
    }

    @PostMapping("/courses/{courseId}/final-test/submissions")
    public ApiResponse<FinalTestSubmissionResponse> submitFinalTest(
            @PathVariable UUID courseId,
            @Valid @RequestBody FinalTestSubmissionRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.LEARNING_FINAL_TEST_SUBMITTED,
                "Final Test submitted.",
                assessmentService.submitFinalTest(courseId, request)
        );
    }

    @PostMapping("/courses/{courseId}/final-test/attempts/{attemptId}/terminate")
    public ApiResponse<Void> terminateFinalTestAttempt(
            @PathVariable UUID courseId,
            @PathVariable UUID attemptId
    ) {
        assessmentService.terminateFinalTestAttempt(courseId, attemptId);
        return ApiResponse.success(
                MessageCodes.COMMON_UPDATED,
                "Final Test attempt terminated because of repeated integrity violations.",
                null
        );
    }
}
