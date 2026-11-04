package com.manabihub.payout.service;

import com.manabihub.payout.dto.request.ManualTransferRequest;
import com.manabihub.payout.dto.request.RejectPayoutRequest;
import com.manabihub.payout.dto.request.PayoutQueueFilterRequest;
import com.manabihub.payout.dto.response.PayoutDecisionResponse;
import com.manabihub.payout.dto.response.PayoutDetailResponse;
import com.manabihub.payout.dto.response.PayoutQueueItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface PayoutSettlementService {

    Page<PayoutQueueItemResponse> getPayoutQueue(
            PayoutQueueFilterRequest filter,
            Pageable pageable
    );

    PayoutDetailResponse getPayoutDetail(UUID withdrawalRequestId);

    PayoutDetailResponse reviewReconciliation(UUID withdrawalRequestId);

    PayoutDecisionResponse approvePayout(UUID withdrawalRequestId);

    PayoutDecisionResponse retryPayout(UUID withdrawalRequestId);

    PayoutDecisionResponse confirmManualTransfer(
            UUID withdrawalRequestId,
            ManualTransferRequest request,
            MultipartFile proof
    );

    PayoutDecisionResponse rejectPayout(UUID withdrawalRequestId, RejectPayoutRequest request);

    PayoutProofDownload getManualTransferProof(UUID withdrawalRequestId);

    PayoutProofDownload getBankQrCode(UUID withdrawalRequestId);

    record PayoutProofDownload(Resource resource, String fileName, String contentType) {
    }
}
