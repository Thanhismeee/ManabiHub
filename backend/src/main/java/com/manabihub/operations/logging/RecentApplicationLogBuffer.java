package com.manabihub.operations.logging;

import com.manabihub.operations.dto.RecentApplicationLogResponse.LogEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

@Component
public class RecentApplicationLogBuffer {

    private final Deque<LogEntry> entries = new ArrayDeque<>();
    private final OperationsLogSanitizer sanitizer;
    private final int capacity;
    private final long maxBytes;
    private final Instant processStartedAt;
    private long currentBytes;

    public RecentApplicationLogBuffer(
            OperationsLogSanitizer sanitizer,
            @Value("${manabihub.operations.log-capacity:500}") int configuredCapacity,
            @Value("${manabihub.operations.log-max-bytes:3145728}") long configuredMaxBytes
    ) {
        this.sanitizer = sanitizer;
        this.capacity = Math.max(100, Math.min(configuredCapacity, 1_000));
        this.maxBytes = Math.max(1_048_576, Math.min(configuredMaxBytes, 4_194_304));
        this.processStartedAt = Instant.ofEpochMilli(
                java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime()
        );
    }

    public void append(LogEntry entry) {
        LogEntry sanitized = sanitize(entry);
        long entryBytes = estimatedBytes(sanitized);
        synchronized (this) {
            while (!entries.isEmpty()
                    && (entries.size() >= capacity || currentBytes + entryBytes > maxBytes)) {
                currentBytes -= estimatedBytes(entries.removeFirst());
            }
            // A single entry is always bounded below maxBytes by field truncation.
            entries.addLast(sanitized);
            currentBytes += entryBytes;
        }
    }

    public List<LogEntry> find(
            String level,
            String query,
            String correlationId,
            int limit
    ) {
        String normalizedLevel = normalize(level);
        String normalizedQuery = normalize(query);
        String normalizedCorrelationId = normalize(correlationId);
        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<LogEntry> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(entries);
        }
        List<LogEntry> result = new ArrayList<>(Math.min(safeLimit, snapshot.size()));
        for (int index = snapshot.size() - 1; index >= 0 && result.size() < safeLimit; index--) {
            LogEntry entry = snapshot.get(index);
            if (normalizedLevel != null && !entry.level().equalsIgnoreCase(normalizedLevel)) {
                continue;
            }
            if (normalizedCorrelationId != null
                    && (entry.correlationId() == null
                    || !entry.correlationId().toLowerCase(Locale.ROOT).contains(normalizedCorrelationId))) {
                continue;
            }
            if (normalizedQuery != null && !searchableText(entry).contains(normalizedQuery)) {
                continue;
            }
            // Defense in depth: re-sanitize at the trust boundary as well as ingestion.
            result.add(sanitize(entry));
        }
        return List.copyOf(result);
    }

    public int capacity() {
        return capacity;
    }

    public Instant processStartedAt() {
        return processStartedAt;
    }

    private LogEntry sanitize(LogEntry entry) {
        return new LogEntry(
                entry.timestamp(),
                truncate(sanitizer.sanitize(entry.level()), 16),
                truncate(sanitizer.sanitize(entry.logger()), 240),
                truncate(sanitizer.sanitize(entry.message()), 2_048),
                truncate(sanitizer.sanitize(entry.correlationId()), 128),
                truncate(sanitizer.sanitize(entry.exception()), 4_096)
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…[TRUNCATED]";
    }

    private long estimatedBytes(LogEntry entry) {
        return 128L + 2L * (
                length(entry.level())
                        + length(entry.logger())
                        + length(entry.message())
                        + length(entry.correlationId())
                        + length(entry.exception())
        );
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String searchableText(LogEntry entry) {
        return String.join(" ",
                nullToEmpty(entry.logger()),
                nullToEmpty(entry.message()),
                nullToEmpty(entry.exception())
        ).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
