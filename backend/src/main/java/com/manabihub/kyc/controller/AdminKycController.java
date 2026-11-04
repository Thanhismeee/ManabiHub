package com.manabihub.kyc.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.kyc.dto.request.KycReviewRequest;
import com.manabihub.kyc.dto.response.KycDocumentDownload;
import com.manabihub.kyc.dto.response.KycRequestResponse;
import com.manabihub.kyc.service.KycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/kyc-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COURSE_MANAGER')")
public class AdminKycController {

    private final KycService kycService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<KycRequestResponse>>> getPendingKycQueue(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        List<KycRequestResponse> queue = kycService.getPendingKycQueue(adminId);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Fetched pending KYC queue successfully",
                queue
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<KycRequestResponse>> getKycDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        KycRequestResponse detail = kycService.getKycDetail(id, adminId);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Fetched KYC detail successfully",
                detail
        ));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<KycRequestResponse>> reviewKyc(
            @PathVariable UUID id,
            @Valid @RequestBody KycReviewRequest reviewRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        KycRequestResponse updated = kycService.reviewKyc(id, reviewRequest, adminId);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.ADMIN_ACTION_SUCCESS,
                "Reviewed KYC successfully",
                updated
        ));
    }

    @GetMapping("/{requestId}/documents/{documentId}")
    public ResponseEntity<byte[]> getKycDocument(
            @PathVariable UUID requestId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        KycDocumentDownload document =
                kycService.getKycDocument(requestId, documentId, adminId);
        MediaType mediaType = MediaType.parseMediaType(document.mimeType());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(document.fileName())
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(document.content());
    }
}
