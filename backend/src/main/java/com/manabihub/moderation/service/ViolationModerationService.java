package com.manabihub.moderation.service;

import com.manabihub.moderation.dto.request.ResolveViolationRequest;
import com.manabihub.moderation.dto.response.ViolationDetailResponse;
import com.manabihub.moderation.dto.response.ViolationQueueItemResponse;
import com.manabihub.moderation.enums.ViolationReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.Resource;

import java.util.UUID;

public interface ViolationModerationService {

    Page<ViolationQueueItemResponse> getViolationQueue(
            ViolationReportStatus status,
            Pageable pageable,
            UUID adminId
    );

    ViolationDetailResponse getViolationDetail(UUID reportId, UUID adminId);

    ViolationDetailResponse resolveViolation(UUID reportId, ResolveViolationRequest request, UUID adminId);

    ViolationEvidenceDownload getViolationEvidence(UUID reportId, UUID evidenceId, UUID adminId);

    record ViolationEvidenceDownload(String fileName, String contentType, Resource resource) {
    }
}
