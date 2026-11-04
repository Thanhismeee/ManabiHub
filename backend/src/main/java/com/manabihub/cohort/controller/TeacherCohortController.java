package com.manabihub.cohort.controller;

import com.manabihub.cohort.dto.AddCohortMemberRequest;
import com.manabihub.cohort.dto.CohortResponse;
import com.manabihub.cohort.dto.CreateCohortRequest;
import com.manabihub.cohort.dto.UpdateCohortStatusRequest;
import com.manabihub.cohort.service.LearningCohortService;
import com.manabihub.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher/cohorts")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherCohortController {

    private final LearningCohortService cohortService;

    @GetMapping
    public ApiResponse<List<CohortResponse>> list() {
        return ApiResponse.success(cohortService.listMine());
    }

    @GetMapping("/{id}")
    public ApiResponse<CohortResponse> detail(@PathVariable UUID id) {
        return ApiResponse.success(cohortService.getMine(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CohortResponse>> create(
            @Valid @RequestBody CreateCohortRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "COHORT_CREATED", "Đã tạo lớp học và sẵn sàng thêm học viên.",
                cohortService.create(request)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<CohortResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCohortStatusRequest request
    ) {
        return ApiResponse.success(cohortService.updateStatus(id, request.status()));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<CohortResponse> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddCohortMemberRequest request
    ) {
        return ApiResponse.success(
                "COHORT_MEMBER_ADDED", "Đã thêm học viên và cấp quyền học.",
                cohortService.addMember(id, request));
    }

    @DeleteMapping("/{cohortId}/members/{memberId}")
    public ApiResponse<CohortResponse> removeMember(
            @PathVariable UUID cohortId,
            @PathVariable UUID memberId
    ) {
        return ApiResponse.success(
                "COHORT_MEMBER_REMOVED", "Đã gỡ học viên khỏi lớp.",
                cohortService.removeMember(cohortId, memberId));
    }
}
