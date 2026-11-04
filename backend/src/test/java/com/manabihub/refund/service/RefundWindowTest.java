package com.manabihub.refund.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class RefundWindowTest {
    @Test
    void includesAllOfDayFourteenInVietnamEvenWhenServerUsesUtc() {
        Instant deadline = RefundWindow.exclusiveDeadline(Instant.parse("2026-09-01T03:00:00Z"), 14);
        assertEquals(Instant.parse("2026-09-15T17:00:00Z"), deadline);
        assertTrue(Instant.parse("2026-09-15T16:59:59Z").isBefore(deadline));
        assertFalse(Instant.parse("2026-09-15T17:00:00Z").isBefore(deadline));
    }

    @Test
    void paymentAfterUtc1700BelongsToNextBusinessDate() {
        assertEquals(Instant.parse("2026-09-16T17:00:00Z"),
                RefundWindow.exclusiveDeadline(Instant.parse("2026-09-01T18:00:00Z"), 14));
    }
}
