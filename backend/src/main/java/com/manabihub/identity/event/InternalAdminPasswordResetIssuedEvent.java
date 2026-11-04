package com.manabihub.identity.event;

import java.time.Instant;
import java.util.UUID;

public record InternalAdminPasswordResetIssuedEvent(
        UUID adminAccountId,
        String email,
        String fullName,
        String rawToken,
        Instant expiresAt
) {
}
