package com.manabihub.learning.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.learning.dto.response.LearningCertificateResponse;
import com.manabihub.learning.service.StudentCertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/courses/{courseId}/certificate")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentCertificateController {

    private final StudentCertificateService certificateService;

    @GetMapping
    public ApiResponse<LearningCertificateResponse> getCertificate(@PathVariable UUID courseId) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Certificate loaded.",
                certificateService.getCertificate(courseId)
        );
    }

    @PostMapping
    public ApiResponse<LearningCertificateResponse> generateCertificate(@PathVariable UUID courseId) {
        return ApiResponse.success(
                MessageCodes.LEARNING_CERTIFICATE_ISSUED,
                "Certificate issued.",
                certificateService.generateCertificate(courseId)
        );
    }
}
