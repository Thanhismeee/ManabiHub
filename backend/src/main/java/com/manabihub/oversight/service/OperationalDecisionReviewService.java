package com.manabihub.oversight.service;

import com.manabihub.common.response.PageResponse;
import com.manabihub.oversight.dto.request.DecisionReviewFilterRequest;
import com.manabihub.oversight.dto.request.DecisionWarningRequest;
import com.manabihub.oversight.dto.response.DecisionReviewDetailResponse;
import com.manabihub.oversight.dto.response.DecisionReviewSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OperationalDecisionReviewService {
    PageResponse<DecisionReviewSummaryResponse> search(
            DecisionReviewFilterRequest filter,
            Pageable pageable
    );

    DecisionReviewDetailResponse get(UUID auditLogId);

    DecisionReviewDetailResponse markReviewed(UUID auditLogId);

    DecisionReviewDetailResponse sendWarning(UUID auditLogId, DecisionWarningRequest request);
}
