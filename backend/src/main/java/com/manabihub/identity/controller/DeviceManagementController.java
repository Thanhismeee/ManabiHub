package com.manabihub.identity.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.identity.entity.PublicUserDevice;
import com.manabihub.identity.repository.PublicUserDeviceRepository;
import com.manabihub.identity.service.PublicUserSessionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/public/devices")
@RequiredArgsConstructor
public class DeviceManagementController {

    private final PublicUserDeviceRepository deviceRepository;
    private final PublicUserSessionService sessionService;

    @GetMapping
    public ApiResponse<List<DeviceResponse>> listDevices(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<PublicUserDevice> devices = deviceRepository.findByUserIdAndRevokedAtIsNull(userId);
        
        List<DeviceResponse> responses = devices.stream().map(device -> {
            DeviceResponse r = new DeviceResponse();
            r.setId(device.getId());
            r.setDisplayName(device.getDisplayName());
            r.setUserAgent(device.getUserAgent());
            r.setLastSeenAt(device.getLastSeenAt().toString());
            return r;
        }).collect(Collectors.toList());
        
        return ApiResponse.success(responses);
    }

    @PostMapping("/{deviceId}/revoke")
    public ApiResponse<Void> revokeDevice(
            @PathVariable UUID deviceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        sessionService.revokeDevice(userId, deviceId);
        return ApiResponse.success(null);
    }

    @Data
    public static class DeviceResponse {
        private UUID id;
        private String displayName;
        private String userAgent;
        private String lastSeenAt;
    }
}
