package com.manabihub.identity.event;

import com.manabihub.identity.enums.RoleCode;

import java.time.Instant;

public record InternalAdminInvitationIssuedEvent(
        String email,
        String fullName,
        RoleCode role,
        String rawToken,
        Instant expiresAt
) {
}
