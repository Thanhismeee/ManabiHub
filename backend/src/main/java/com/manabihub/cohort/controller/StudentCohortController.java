package com.manabihub.cohort.controller;

import com.manabihub.cohort.dto.CohortWorkspaceDto.Workspace;
import com.manabihub.cohort.dto.CohortWorkspaceDto.ClassSummary;
import com.manabihub.cohort.service.CohortWorkspaceService;
import com.manabihub.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/student/cohorts") @PreAuthorize("hasRole('STUDENT')")
public class StudentCohortController {
    private final CohortWorkspaceService service;
    @GetMapping public ApiResponse<List<ClassSummary>> list() { return ApiResponse.success(service.studentClasses()); }
    @GetMapping("/{id}") public ApiResponse<Workspace> detail(@PathVariable UUID id) { return ApiResponse.success(service.studentWorkspace(id)); }
    @PostMapping(value = "/{id}/assignments/{assignmentId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Workspace> submit(@PathVariable UUID id, @PathVariable UUID assignmentId,
            @RequestParam(required = false) String content, @RequestParam(required = false) Integer reportedAudioSeconds,
            @RequestPart(required = false) MultipartFile audio) {
        return ApiResponse.success(service.submit(id, assignmentId, content, reportedAudioSeconds, audio));
    }
    @GetMapping("/submissions/{submissionId}/audio")
    public ResponseEntity<Resource> audio(@PathVariable UUID submissionId) {
        var audio = service.audio(submissionId, false);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(audio.mimeType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(audio.resource());
    }
}
