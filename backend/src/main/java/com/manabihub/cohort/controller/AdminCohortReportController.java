package com.manabihub.cohort.controller;

import com.manabihub.cohort.dto.CohortWorkspaceDto.Report;
import com.manabihub.cohort.dto.CohortWorkspaceDto.ReportPage;
import com.manabihub.cohort.service.CohortWorkspaceService;
import com.manabihub.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Organization and branch are report dimensions; existing internal roles govern access. */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/admin/cohort-reports")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COURSE_MANAGER')")
public class AdminCohortReportController {
    private final CohortWorkspaceService service;
    @GetMapping public ApiResponse<ReportPage> reports(@RequestParam(required=false) String organization, @RequestParam(required=false) String branch,
            @RequestParam(required=false) java.time.LocalDate from, @RequestParam(required=false) java.time.LocalDate to,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) {
        return ApiResponse.success(service.reports(true, organization, branch, from, to, page, size));
    }
}
