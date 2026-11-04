package com.manabihub.course.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.course.dto.response.TeacherDashboardResponse;
import com.manabihub.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher/dashboard")
public class TeacherDashboardController {

    private final CourseService courseService;

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<TeacherDashboardResponse>> getDashboardStats() {
        TeacherDashboardResponse response = courseService.getTeacherDashboardStats();
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.MSG_COURSE_001,
                "Dashboard statistics loaded.",
                response
        ));
    }
}
