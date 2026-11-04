package com.manabihub.identity.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.identity.dto.request.UpdateTeacherProfileRequest;
import com.manabihub.identity.dto.request.RequestPhoneVerificationRequest;
import com.manabihub.identity.dto.request.ConfirmPhoneVerificationRequest;
import com.manabihub.identity.dto.response.TeacherProfileResponse;
import com.manabihub.identity.dto.response.PhoneVerificationResponse;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.identity.service.PhoneVerificationService;
import com.manabihub.identity.service.TeacherProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/teacher/profile")
@RequiredArgsConstructor
public class TeacherProfileController {

    private final TeacherProfileService teacherProfileService;
    private final CurrentUserService currentUserService;
    private final PhoneVerificationService phoneVerificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<TeacherProfileResponse>> getMyProfile() {

        TeacherProfileResponse response =
                teacherProfileService.getMyProfile();

        return ResponseEntity.ok(
                ApiResponse.success(
                        MessageCodes.COMMON_SUCCESS,
                        "Success",
                        response
                )
        );
    }

    @PutMapping
    public ResponseEntity<ApiResponse<TeacherProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateTeacherProfileRequest request
    ) {

        TeacherProfileResponse response =
                teacherProfileService.updateMyProfile(request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        MessageCodes.MSG_PRO_001,
                        "Profile updated successfully",
                        response
                )
        );
    }

    @PostMapping("/phone-verification/request")
    public ResponseEntity<ApiResponse<PhoneVerificationResponse>> requestPhoneVerification(
            @Valid @RequestBody RequestPhoneVerificationRequest request
    ) {
        PhoneVerificationResponse response = phoneVerificationService.requestCode(
                currentUserService.getCurrentUserId(), request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.PHONE_VERIFICATION_OTP_SENT,
                "Verification code sent.",
                response));
    }

    @PostMapping("/phone-verification/confirm")
    public ResponseEntity<ApiResponse<PhoneVerificationResponse>> confirmPhoneVerification(
            @Valid @RequestBody ConfirmPhoneVerificationRequest request
    ) {
        PhoneVerificationResponse response = phoneVerificationService.confirmCode(
                currentUserService.getCurrentUserId(),
                request.phoneNumber(),
                request.code(),
                request.challengeId(),
                request.firebaseIdToken());
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Phone number verified.",
                response));
    }

}
