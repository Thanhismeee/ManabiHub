package com.manabihub.operations.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.operations.dto.OperationsOverviewResponse;
import com.manabihub.operations.dto.RecentApplicationLogResponse;
import com.manabihub.operations.dto.RuntimeConfigurationResponse;
import com.manabihub.operations.service.OperationsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/operations")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminOperationsController {

    private final OperationsService operationsService;

    @GetMapping("/overview")
    public ApiResponse<OperationsOverviewResponse> getOverview() {
        return ApiResponse.success(operationsService.getOverview());
    }

    @GetMapping("/runtime-config")
    public ApiResponse<RuntimeConfigurationResponse> getRuntimeConfiguration() {
        return ApiResponse.success(operationsService.getRuntimeConfiguration());
    }

    @GetMapping("/logs")
    public ApiResponse<RecentApplicationLogResponse> getRecentLogs(
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)INFO|WARN|ERROR", message = "Unsupported log level")
            String level,
            @RequestParam(required = false) @Size(max = 120) String query,
            @RequestParam(required = false) @Size(max = 128) String correlationId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit
    ) {
        return ApiResponse.success(
                operationsService.getRecentLogs(level, query, correlationId, limit)
        );
    }
}
