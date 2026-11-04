package com.manabihub.learning.service;

import com.manabihub.learning.dto.request.FinalTestSubmissionRequest;
import com.manabihub.learning.dto.request.QuizSubmissionRequest;
import com.manabihub.learning.dto.response.FinalTestEligibilityResponse;
import com.manabihub.learning.dto.response.FinalTestStartResponse;
import com.manabihub.learning.dto.response.FinalTestSubmissionResponse;
import com.manabihub.learning.dto.response.QuizSubmissionResponse;

import java.util.UUID;

public interface StudentAssessmentService {
    QuizSubmissionResponse submitQuiz(UUID lessonBlockId, QuizSubmissionRequest request);

    FinalTestEligibilityResponse getFinalTestEligibility(UUID courseId);

    FinalTestStartResponse startFinalTest(UUID courseId);

    FinalTestSubmissionResponse submitFinalTest(UUID courseId, FinalTestSubmissionRequest request);

    void terminateFinalTestAttempt(UUID courseId, UUID attemptId);
}
