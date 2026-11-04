package com.manabihub.identity.event;

import java.time.Instant;
import java.util.UUID;

public record InternalAdminPasswordChangedEvent(
        UUID adminAccountId,
        String email,
        String fullName,
        Instant changedAt
) {
}
