package com.manabihub.operations.logging;

import com.manabihub.operations.dto.RecentApplicationLogResponse.LogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentApplicationLogBufferTest {

    @Test
    void boundsEntriesReturnsNewestFirstAndCapsRequestedLimit() {
        RecentApplicationLogBuffer buffer = buffer(100);
        for (int index = 0; index < 105; index++) {
            buffer.append(entry("INFO", "event-" + index, "cid-" + index));
        }

        List<LogEntry> result = buffer.find(null, null, null, 500);

        assertThat(result).hasSize(100);
        assertThat(result.get(0).message()).isEqualTo("event-104");
        assertThat(result.get(99).message()).isEqualTo("event-5");
    }

    @Test
    void filtersByLevelQueryAndCorrelationId() {
        RecentApplicationLogBuffer buffer = buffer(100);
        buffer.append(entry("INFO", "payment accepted", "req-one"));
        buffer.append(entry("ERROR", "payment gateway timeout", "req-two"));
        buffer.append(entry("ERROR", "mail gateway timeout", "req-three"));

        List<LogEntry> result = buffer.find("error", "payment", "req-two", 10);

        assertThat(result).extracting(LogEntry::message)
                .containsExactly("payment gateway timeout");
    }

    @Test
    void sanitizesAtIngestionAndAgainAtOutputAndTruncatesFields() {
        RecentApplicationLogBuffer buffer = buffer(100);
        buffer.append(new LogEntry(
                Instant.now(),
                "ERROR",
                "com.manabihub.Test",
                "email=user@example.com password=super-secret " + "x".repeat(3_000),
                "correlation-1",
                "Bearer top-secret " + "y".repeat(5_000)
        ));

        LogEntry result = buffer.find(null, null, null, 1).get(0);

        assertThat(result.message())
                .doesNotContain("user@example.com", "super-secret")
                .hasSizeLessThan(2_070);
        assertThat(result.exception())
                .doesNotContain("top-secret")
                .hasSizeLessThan(4_120);
    }

    private RecentApplicationLogBuffer buffer(int capacity) {
        return new RecentApplicationLogBuffer(
                new OperationsLogSanitizer(),
                capacity,
                3_145_728
        );
    }

    private LogEntry entry(String level, String message, String correlationId) {
        return new LogEntry(
                Instant.now(),
                level,
                "com.manabihub.Test",
                message,
                correlationId,
                null
        );
    }
}
