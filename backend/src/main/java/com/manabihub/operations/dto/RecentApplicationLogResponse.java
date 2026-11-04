package com.manabihub.operations.dto;

import java.time.Instant;
import java.util.List;

public record RecentApplicationLogResponse(
        String logSource,
        Instant currentProcessStartedAt,
        int capacity,
        int returned,
        List<LogEntry> items
) {
    public record LogEntry(
            Instant timestamp,
            String level,
            String logger,
            String message,
            String correlationId,
            String exception
    ) {
    }
}
