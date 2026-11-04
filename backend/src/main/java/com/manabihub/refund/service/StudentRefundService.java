package com.manabihub.refund.service;

import com.manabihub.common.response.PageResponse;
import com.manabihub.refund.dto.request.CreateStudentRefundRequest;
import com.manabihub.refund.dto.response.StudentRefundResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StudentRefundService {
    StudentRefundResponse createRefundRequest(UUID userId, CreateStudentRefundRequest request);
    PageResponse<StudentRefundResponse> getMyRefundRequests(UUID userId, Pageable pageable);
    StudentRefundResponse getMyRefundDetail(UUID userId, UUID refundId);
    StudentRefundResponse cancelRefundRequest(UUID userId, UUID refundId);
}
