package com.manabihub.systemconfig.dto.response;

import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.InternalAdminInvitationStatus;
import com.manabihub.identity.enums.RoleCode;

import java.time.Instant;
import java.util.UUID;

public record InternalAdminAccountResponse(
        UUID id,
        String email,
        String fullName,
        AccountStatus status,
        RoleCode role,
        Instant lastLoginAt,
        Instant updatedAt,
        InternalAdminInvitationStatus invitationStatus,
        Instant invitationExpiresAt
) {
}
