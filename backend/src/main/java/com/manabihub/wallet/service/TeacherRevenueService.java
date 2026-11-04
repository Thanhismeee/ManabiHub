package com.manabihub.wallet.service;

import com.manabihub.wallet.dto.response.TeacherRevenueSummaryResponse;

import java.util.UUID;

public interface TeacherRevenueService {
    TeacherRevenueSummaryResponse getRevenueSummary(UUID userId);
}
