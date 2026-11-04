package com.manabihub.kyc.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherCredentialType;
import com.manabihub.kyc.dto.TeacherQualificationEvidenceDownload;
import com.manabihub.kyc.dto.TeacherQualificationResponse;
import com.manabihub.kyc.service.TeacherQualificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher/kyc/qualifications")
@PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
public class TeacherQualificationController {

    private final TeacherQualificationService qualificationService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<List<TeacherQualificationResponse>> listMine() {
        return ApiResponse.success(qualificationService.listMine(currentUserService.getCurrentUserId()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TeacherQualificationResponse>> submit(
            @RequestParam LearningLanguage learningLanguage,
            @RequestParam ProficiencyFramework proficiencyFramework,
            @RequestParam(defaultValue = "CURRENT") String frameworkVersion,
            @RequestParam String proficiencyLevel,
            @RequestParam TeacherCredentialType credentialType,
            @RequestParam(required = false) String credentialCode,
            @RequestParam String issuer,
            @RequestParam boolean copyrightAgreementAccepted,
            @RequestParam MultipartFile evidence
    ) {
        TeacherQualificationResponse response = qualificationService.submit(
                currentUserService.getCurrentUserId(),
                learningLanguage,
                proficiencyFramework,
                frameworkVersion,
                proficiencyLevel,
                credentialType,
                credentialCode,
                issuer,
                copyrightAgreementAccepted,
                evidence);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "TEACHER_QUALIFICATION_SUBMITTED",
                "Hồ sơ năng lực đã được gửi để xét duyệt.",
                response));
    }

    @GetMapping("/{id}/evidence")
    public ResponseEntity<byte[]> evidence(@PathVariable UUID id) {
        TeacherQualificationEvidenceDownload evidence = qualificationService.getTeacherEvidence(
                id, currentUserService.getCurrentUserId());
        return evidenceResponse(evidence);
    }

    private ResponseEntity<byte[]> evidenceResponse(TeacherQualificationEvidenceDownload evidence) {
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
