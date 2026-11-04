package com.manabihub.course.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.course.dto.response.PublicTeacherProfileResponse;
import com.manabihub.course.dto.response.PublicTeacherSummaryResponse;
import com.manabihub.course.service.PublicTeacherProfileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/teachers")
@RequiredArgsConstructor
@Validated
public class PublicTeacherProfileController {

    private final PublicTeacherProfileService publicTeacherProfileService;

    @GetMapping
    public ApiResponse<List<PublicTeacherSummaryResponse>> listFeatured(
            @RequestParam(defaultValue = "4")
            @Min(value = 1, message = "Limit must not be less than one")
            @Max(value = 12, message = "Limit must not be greater than twelve")
            int limit
    ) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Success",
                publicTeacherProfileService.listFeatured(limit)
        );
    }

    @GetMapping("/{teacherId}")
    public ApiResponse<PublicTeacherProfileResponse> getProfile(@PathVariable UUID teacherId) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Success",
                publicTeacherProfileService.getProfile(teacherId)
        );
    }
}
