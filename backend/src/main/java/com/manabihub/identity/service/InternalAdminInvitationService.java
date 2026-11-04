package com.manabihub.identity.service;

import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.enums.InternalAdminInvitationStatus;
import com.manabihub.identity.enums.RoleCode;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface InternalAdminInvitationService {

    InternalAdminAccount invite(
            UUID actorId,
            String email,
            String fullName,
            RoleCode roleCode,
            String reason
    );

    InternalAdminAccount resend(UUID actorId, UUID accountId, String reason);

    void revokeOpenInvitations(UUID accountId, Instant revokedAt);

    void accept(String rawToken, String password, String ipAddress, String userAgent);

    Map<UUID, InvitationSummary> latestInvitationSummaries(Collection<UUID> accountIds);

    record InvitationSummary(
            InternalAdminInvitationStatus status,
            Instant expiresAt
    ) {
        public static InvitationSummary none() {
            return new InvitationSummary(InternalAdminInvitationStatus.NONE, null);
        }
    }
}
