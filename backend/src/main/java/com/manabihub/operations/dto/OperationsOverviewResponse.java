package com.manabihub.operations.dto;

import java.time.Instant;
import java.util.List;

public record OperationsOverviewResponse(
        String applicationStatus,
        String databaseStatus,
        Instant startedAt,
        long uptimeSeconds,
        List<String> activeProfiles,
        String timezone,
        String businessTimezone,
        String javaVersion,
        String jvmName,
        MemorySnapshot memory,
        ThreadSnapshot threads,
        BuildSnapshot build
) {
    public record MemorySnapshot(
            long heapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            long nonHeapUsedBytes
    ) {
    }

    public record ThreadSnapshot(
            int live,
            int daemon,
            int peak
    ) {
    }

    public record BuildSnapshot(
            String applicationName,
            String version,
            Instant buildTime,
            String gitCommit
    ) {
    }
}
