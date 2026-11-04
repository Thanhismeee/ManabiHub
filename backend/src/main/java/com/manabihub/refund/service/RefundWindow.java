package com.manabihub.refund.service;

import java.time.Instant;
import java.time.ZoneId;

/** BR-REF-01 includes the entire final calendar day in the business timezone. */
public final class RefundWindow {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private RefundWindow() { }

    public static Instant exclusiveDeadline(Instant paidAt, int days) {
        return paidAt.atZone(BUSINESS_ZONE).toLocalDate().plusDays((long) days + 1)
                .atStartOfDay(BUSINESS_ZONE).toInstant();
    }
}
