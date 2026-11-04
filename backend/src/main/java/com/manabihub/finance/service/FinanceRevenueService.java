package com.manabihub.finance.service;

import com.manabihub.finance.dto.response.RevenueDashboardResponse;
import com.manabihub.finance.enums.RevenueGranularity;

import java.time.Instant;

public interface FinanceRevenueService {
    RevenueDashboardResponse getDashboard(Instant from, Instant to, RevenueGranularity granularity);
}
