package com.manabihub.refund.service;

import com.manabihub.common.response.PageResponse;
import com.manabihub.refund.dto.request.RefundDecisionRequest;
import com.manabihub.refund.dto.request.RefundQueueFilterRequest;
import com.manabihub.refund.dto.response.RefundDetailResponse;
import com.manabihub.refund.dto.response.RefundQueueResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RefundService {

    PageResponse<RefundQueueResponse> searchRefunds(RefundQueueFilterRequest filter, Pageable pageable);

    RefundDetailResponse getRefundDetail(UUID refundId);

    void approveRefund(UUID refundId, RefundDecisionRequest request);

    void rejectRefund(UUID refundId, RefundDecisionRequest request);
}
