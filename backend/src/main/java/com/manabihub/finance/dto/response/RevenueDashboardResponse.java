package com.manabihub.finance.dto.response;

import com.manabihub.finance.enums.RevenueGranularity;

import java.time.Instant;
import java.util.List;

public record RevenueDashboardResponse(
        Instant from,
        Instant to,
        String timezone,
        RevenueGranularity granularity,
        Instant generatedAt,
        RevenueSummaryResponse summary,
        List<RevenueTimePointResponse> points
) {
}
