package com.manabihub.cohort.controller;

import com.manabihub.cohort.dto.CohortWorkspaceDto.*;
import com.manabihub.cohort.service.CohortWorkspaceService;
import com.manabihub.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/teacher/cohorts") @PreAuthorize("hasRole('TEACHER')")
public class TeacherCohortWorkspaceController {
    private final CohortWorkspaceService service;
    @GetMapping("/{id}/workspace")
    public ApiResponse<Workspace> workspace(@PathVariable UUID id) { return ApiResponse.success(service.teacherWorkspace(id)); }
    @PostMapping("/{id}/assignments")
    public ApiResponse<Workspace> create(@PathVariable UUID id, @Valid @RequestBody CreateAssignment request) { return ApiResponse.success(service.createAssignment(id, request)); }
    @PostMapping("/{id}/assignments/{assignmentId}/close")
    public ApiResponse<Workspace> close(@PathVariable UUID id, @PathVariable UUID assignmentId) { return ApiResponse.success(service.closeAssignment(id, assignmentId)); }
    @PatchMapping("/{id}/feedback-package")
    public ApiResponse<Workspace> configure(@PathVariable UUID id, @Valid @RequestBody PackageSettings request) { return ApiResponse.success(service.updatePackage(id, request)); }
    @PostMapping("/{id}/submissions/{submissionId}/feedback")
    public ApiResponse<Workspace> review(@PathVariable UUID id, @PathVariable UUID submissionId, @Valid @RequestBody Feedback request) { return ApiResponse.success(service.review(id, submissionId, request)); }
    @PostMapping("/{id}/members/{memberId}/remind")
    public ApiResponse<Workspace> remind(@PathVariable UUID id, @PathVariable UUID memberId) { return ApiResponse.success(service.remind(id, memberId)); }
    @GetMapping("/reports")
    public ApiResponse<ReportPage> reports(@RequestParam(required=false) String organization, @RequestParam(required=false) String branch,
            @RequestParam(required=false) java.time.LocalDate from, @RequestParam(required=false) java.time.LocalDate to,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) {
        return ApiResponse.success(service.reports(false, organization, branch, from, to, page, size));
    }
    @GetMapping("/submissions/{submissionId}/audio")
    public ResponseEntity<Resource> audio(@PathVariable UUID submissionId) {
        var audio = service.audio(submissionId, true);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(audio.mimeType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(audio.resource());
    }
}
