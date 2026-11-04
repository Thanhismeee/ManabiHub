package com.manabihub.systemconfig.service;

import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.systemconfig.dto.response.InternalAdminAccountResponse;
import com.manabihub.systemconfig.dto.response.SystemSettingResponse;

import java.util.List;
import java.util.UUID;

public interface SystemAdministrationService {

    List<SystemSettingResponse> listSettings(UUID actorId);

    SystemSettingResponse updateSetting(
            UUID actorId,
            String settingKey,
            String value,
            String reason
    );

    List<InternalAdminAccountResponse> listInternalAdmins(UUID actorId);

    InternalAdminAccountResponse inviteInternalAdmin(
            UUID actorId,
            String email,
            String fullName,
            RoleCode roleCode,
            String reason
    );

    InternalAdminAccountResponse resendInternalAdminInvitation(
            UUID actorId,
            UUID targetAdminId,
            String reason
    );

    InternalAdminAccountResponse updateInternalAdminRole(
            UUID actorId,
            UUID targetAdminId,
            RoleCode roleCode,
            String reason
    );

    InternalAdminAccountResponse updateInternalAdminStatus(
            UUID actorId,
            UUID targetAdminId,
            AccountStatus status,
            String reason
    );
}
