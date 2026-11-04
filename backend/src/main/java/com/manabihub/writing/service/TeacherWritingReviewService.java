package com.manabihub.writing.service;

import com.manabihub.common.response.PageResponse;
import com.manabihub.writing.dto.request.TeacherWritingFeedbackRequest;
import com.manabihub.writing.dto.response.WritingSubmissionDetailResponse;
import com.manabihub.writing.dto.response.WritingReviewFacetResponse;
import com.manabihub.writing.dto.response.WritingReviewOverviewResponse;
import com.manabihub.writing.dto.response.WritingSubmissionSummaryResponse;
import com.manabihub.writing.enums.WritingSubmissionStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TeacherWritingReviewService {
    PageResponse<WritingSubmissionSummaryResponse> listSubmissions(
            String searchQuery,
            Boolean reviewed,
            UUID courseId,
            UUID lessonId,
            WritingSubmissionStatus status,
            Pageable pageable
    );

    WritingReviewFacetResponse getFacets();

    WritingReviewOverviewResponse getOverview(
            String searchQuery,
            UUID courseId,
            UUID lessonId,
            WritingSubmissionStatus status
    );

    WritingSubmissionDetailResponse getSubmission(UUID submissionId);

    WritingSubmissionDetailResponse saveFeedback(
            UUID submissionId,
            TeacherWritingFeedbackRequest request
    );
}
