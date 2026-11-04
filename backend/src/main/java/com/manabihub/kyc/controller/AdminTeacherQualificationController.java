package com.manabihub.kyc.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.kyc.domain.TeacherQualificationStatus;
import com.manabihub.kyc.dto.TeacherQualificationDecisionRequest;
import com.manabihub.kyc.dto.TeacherQualificationEvidenceDownload;
import com.manabihub.kyc.dto.TeacherQualificationResponse;
import com.manabihub.kyc.service.TeacherQualificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/teacher-qualifications")
@PreAuthorize("hasAnyRole('COURSE_MANAGER', 'SYSTEM_ADMIN')")
public class AdminTeacherQualificationController {

    private final TeacherQualificationService qualificationService;

    @GetMapping
    public ApiResponse<List<TeacherQualificationResponse>> list(
            @RequestParam(required = false) TeacherQualificationStatus status
    ) {
        return ApiResponse.success(qualificationService.listForReview(status));
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<TeacherQualificationResponse> decide(
            @PathVariable UUID id,
            @Valid @RequestBody TeacherQualificationDecisionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(
                "TEACHER_QUALIFICATION_REVIEWED",
                "Đã cập nhật kết quả duyệt năng lực giáo viên.",
                qualificationService.decide(id, request, UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/{id}/evidence")
    public ResponseEntity<byte[]> evidence(@PathVariable UUID id) {
        TeacherQualificationEvidenceDownload evidence = qualificationService.getAdminEvidence(id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(evidence.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(evidence.mimeType()))
                .body(evidence.content());
    }
}
