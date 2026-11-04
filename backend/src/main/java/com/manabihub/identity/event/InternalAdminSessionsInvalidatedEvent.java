package com.manabihub.identity.event;

import java.time.Instant;
import java.util.UUID;

public record InternalAdminSessionsInvalidatedEvent(
        UUID adminAccountId,
        String reason,
        Instant occurredAt
) {
}
