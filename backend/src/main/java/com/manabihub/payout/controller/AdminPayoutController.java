package com.manabihub.payout.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.payout.dto.request.ManualTransferRequest;
import com.manabihub.payout.dto.request.RejectPayoutRequest;
import com.manabihub.payout.dto.request.PayoutQueueFilterRequest;
import com.manabihub.payout.dto.response.PayoutDecisionResponse;
import com.manabihub.payout.dto.response.PayoutDetailResponse;
import com.manabihub.payout.dto.response.PayoutQueueItemResponse;
import com.manabihub.payout.service.PayoutSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FINANCE_MANAGER')")
@Tag(name = "Admin Payout", description = "Finance Manager APIs for payout settlement")
public class AdminPayoutController {

    private final PayoutSettlementService payoutSettlementService;

    @Operation(summary = "Get payout queue")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PayoutQueueItemResponse>>> getPayoutQueue(
            @ModelAttribute PayoutQueueFilterRequest filter,
            @PageableDefault(sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Fetched payout queue successfully",
                payoutSettlementService.getPayoutQueue(filter, pageable)
        ));
    }

    @Operation(summary = "Persist a reconciliation review")
    @PostMapping("/{withdrawalRequestId}/reconcile")
    public ResponseEntity<ApiResponse<PayoutDetailResponse>> reviewReconciliation(
            @PathVariable UUID withdrawalRequestId) {
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Reconciliation review recorded",
                payoutSettlementService.reviewReconciliation(withdrawalRequestId)
        ));
    }

    @Operation(summary = "Get payout details")
    @GetMapping("/{withdrawalRequestId}")
    public ResponseEntity<ApiResponse<PayoutDetailResponse>> getPayoutDetail(@PathVariable UUID withdrawalRequestId) {
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Fetched payout detail successfully",
                payoutSettlementService.getPayoutDetail(withdrawalRequestId)
        ));
    }

    @Operation(summary = "Retry or resume a failed/stale gateway payout")
    @PostMapping("/{withdrawalRequestId}/retry")
    public ResponseEntity<ApiResponse<PayoutDecisionResponse>> retryPayout(
            @PathVariable UUID withdrawalRequestId) {
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.MSG_ADM_004,
                "Payout retry completed",
                payoutSettlementService.retryPayout(withdrawalRequestId)
        ));
    }

    @Operation(summary = "Confirm a manual bank transfer with private evidence")
    @PostMapping(
            value = "/{withdrawalRequestId}/manual-transfer",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<PayoutDecisionResponse>> confirmManualTransfer(
            @PathVariable UUID withdrawalRequestId,
            @Valid @RequestPart("metadata") ManualTransferRequest request,
            @RequestPart("proof") MultipartFile proof) {
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.MSG_ADM_004,
                "Manual payout transfer confirmed",
                payoutSettlementService.confirmManualTransfer(withdrawalRequestId, request, proof)
        ));
    }

    @Operation(summary = "Download private manual transfer evidence")
    @GetMapping("/{withdrawalRequestId}/manual-transfer/proof")
    public ResponseEntity<org.springframework.core.io.Resource> getManualTransferProof(
            @PathVariable UUID withdrawalRequestId) {
        PayoutSettlementService.PayoutProofDownload proof =
                payoutSettlementService.getManualTransferProof(withdrawalRequestId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(proof.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .body(proof.resource());
    }

    @Operation(summary = "Download teacher bank QR")
    @GetMapping("/{withdrawalRequestId}/bank-qr")
    public ResponseEntity<org.springframework.core.io.Resource> getBankQr(
            @PathVariable UUID withdrawalRequestId) {
        PayoutSettlementService.PayoutProofDownload qr =
                payoutSettlementService.getBankQrCode(withdrawalRequestId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(qr.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(qr.contentType()))
                .body(qr.resource());
    }

    @Operation(summary = "Approve payout", description = "Reconciles the request and executes an idempotent provider transfer.")
    @PostMapping("/{withdrawalRequestId}/approve")
    public ResponseEntity<ApiResponse<PayoutDecisionResponse>> approvePayout(
            @PathVariable UUID withdrawalRequestId) {
        PayoutDecisionResponse decision = payoutSettlementService.approvePayout(withdrawalRequestId);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.MSG_ADM_004,
                "Payout settlement completed",
                decision
        ));
    }

    @Operation(summary = "Reject payout")
    @PostMapping("/{withdrawalRequestId}/reject")
    public ResponseEntity<ApiResponse<PayoutDecisionResponse>> rejectPayout(
            @PathVariable UUID withdrawalRequestId,
            @Valid @RequestBody RejectPayoutRequest request) {
        PayoutDecisionResponse decision = payoutSettlementService.rejectPayout(withdrawalRequestId, request);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.PAYOUT_REJECTED,
                "Payout request rejected",
                decision
        ));
    }
}
