package com.manabihub.kyc.service;

import com.manabihub.kyc.dto.request.KycReviewRequest;
import com.manabihub.kyc.dto.response.KycDocumentDownload;
import com.manabihub.kyc.dto.response.KycRequestResponse;
import java.util.List;
import java.util.UUID;

public interface KycService {

    List<KycRequestResponse> getPendingKycQueue(UUID adminId);

    KycRequestResponse getKycDetail(UUID id, UUID adminId);

    KycRequestResponse reviewKyc(UUID id, KycReviewRequest request, UUID adminId);

    KycDocumentDownload getKycDocument(UUID requestId, UUID documentId, UUID adminId);
}
