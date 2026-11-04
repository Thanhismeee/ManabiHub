package com.manabihub.identity.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.identity.dto.request.StudentIdentityVerificationRequest;
import com.manabihub.identity.dto.response.StudentIdentityVerificationResponse;
import com.manabihub.identity.service.StudentIdentityVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/student/identity-verifications")
public class StudentIdentityVerificationController {

    private final StudentIdentityVerificationService service;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<StudentIdentityVerificationResponse>> status() {
        return ResponseEntity.ok(ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Success", service.getStatus()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentIdentityVerificationResponse>> verify(
            @Valid @RequestBody StudentIdentityVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(MessageCodes.COMMON_UPDATED, "Identity verified", service.verify(request)));
    }
}
