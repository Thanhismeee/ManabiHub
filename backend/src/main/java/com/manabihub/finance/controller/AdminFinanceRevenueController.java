package com.manabihub.finance.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.finance.dto.response.RevenueDashboardResponse;
import com.manabihub.finance.enums.RevenueGranularity;
import com.manabihub.finance.service.FinanceRevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/finance/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FINANCE_MANAGER')")
public class AdminFinanceRevenueController {

    private final FinanceRevenueService financeRevenueService;

    @GetMapping("/dashboard")
    public ApiResponse<RevenueDashboardResponse> getDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) RevenueGranularity granularity
    ) {
        return ApiResponse.success(financeRevenueService.getDashboard(from, to, granularity));
    }
}
