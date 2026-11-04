package com.manabihub.finaltest.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.finaltest.dto.request.UpdateFinalTestRequest;
import com.manabihub.finaltest.dto.response.FinalTestResponse;
import com.manabihub.finaltest.service.FinalTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher/courses/{courseId}/final-test")
@RequiredArgsConstructor
public class FinalTestController {

    private final FinalTestService finalTestService;

    @GetMapping
    public ResponseEntity<ApiResponse<FinalTestResponse>> getFinalTest(
            @PathVariable UUID courseId) {

        FinalTestResponse response = finalTestService.getFinalTest(courseId);
        return ResponseEntity.ok(ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Success", response));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<FinalTestResponse>> updateFinalTest(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateFinalTestRequest request) {

        FinalTestResponse response = finalTestService.updateFinalTest(courseId, request);
        return ResponseEntity.ok(ApiResponse.success(MessageCodes.COMMON_UPDATED, "Success", response));
    }
}
