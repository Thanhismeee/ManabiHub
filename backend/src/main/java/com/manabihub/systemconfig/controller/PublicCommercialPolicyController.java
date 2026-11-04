package com.manabihub.systemconfig.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.systemconfig.dto.CommercialPolicyDto;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/commercial-policy")
@RequiredArgsConstructor
public class PublicCommercialPolicyController {

    private final CommercialPolicyService commercialPolicyService;

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<CommercialPolicyDto>> getCurrentPolicy() {
        CommercialPolicyDto policy = CommercialPolicyDto.from(
                commercialPolicyService.getCurrentPolicy());
        return ResponseEntity.ok(ApiResponse.success(policy));
    }
}
