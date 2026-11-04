package com.manabihub.systemconfig.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.systemconfig.dto.request.UpdateInternalAdminRoleRequest;
import com.manabihub.systemconfig.dto.request.UpdateInternalAdminStatusRequest;
import com.manabihub.systemconfig.dto.request.UpdateSystemSettingRequest;
import com.manabihub.systemconfig.dto.request.InviteInternalAdminRequest;
import com.manabihub.systemconfig.dto.request.ResendInternalAdminInvitationRequest;
import com.manabihub.systemconfig.dto.response.InternalAdminAccountResponse;
import com.manabihub.systemconfig.dto.response.SystemSettingResponse;
import com.manabihub.systemconfig.service.SystemAdministrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemAdministrationController {

    private final SystemAdministrationService administrationService;

    @GetMapping("/system-settings")
    public ApiResponse<List<SystemSettingResponse>> listSettings(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(administrationService.listSettings(actorId(jwt)));
    }

    @PutMapping("/system-settings/{settingKey}")
    public ApiResponse<SystemSettingResponse> updateSetting(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String settingKey,
            @Valid @RequestBody UpdateSystemSettingRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.SYSTEM_SETTING_UPDATED,
                "System setting updated",
                administrationService.updateSetting(
                        actorId(jwt),
                        settingKey,
                        request.value(),
                        request.reason()
                )
        );
    }

    @GetMapping("/internal-accounts")
    public ApiResponse<List<InternalAdminAccountResponse>> listInternalAdmins(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(administrationService.listInternalAdmins(actorId(jwt)));
    }

    @PostMapping("/internal-accounts/invitations")
    public ApiResponse<InternalAdminAccountResponse> inviteInternalAdmin(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InviteInternalAdminRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.INTERNAL_ADMIN_INVITATION_CREATED,
                "Internal administrator invitation queued",
                administrationService.inviteInternalAdmin(
                        actorId(jwt),
                        request.email(),
                        request.fullName(),
                        request.roleCode(),
                        request.reason()
                )
        );
    }

    @PostMapping("/internal-accounts/{adminId}/invitation/resend")
    public ApiResponse<InternalAdminAccountResponse> resendInternalAdminInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID adminId,
            @Valid @RequestBody ResendInternalAdminInvitationRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.INTERNAL_ADMIN_INVITATION_RESENT,
                "Internal administrator invitation queued again",
                administrationService.resendInternalAdminInvitation(
                        actorId(jwt),
                        adminId,
                        request.reason()
                )
        );
    }

    @PatchMapping("/internal-accounts/{adminId}/role")
    public ApiResponse<InternalAdminAccountResponse> updateInternalAdminRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID adminId,
            @Valid @RequestBody UpdateInternalAdminRoleRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.INTERNAL_ROLE_UPDATED,
                "Internal administrator role updated; the target must sign in again",
                administrationService.updateInternalAdminRole(
                        actorId(jwt),
                        adminId,
                        request.roleCode(),
                        request.reason()
                )
        );
    }

    @PatchMapping("/internal-accounts/{adminId}/status")
    public ApiResponse<InternalAdminAccountResponse> updateInternalAdminStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID adminId,
            @Valid @RequestBody UpdateInternalAdminStatusRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.INTERNAL_ADMIN_STATUS_UPDATED,
                "Internal administrator access status updated",
                administrationService.updateInternalAdminStatus(
                        actorId(jwt),
                        adminId,
                        request.status(),
                        request.reason()
                )
        );
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
