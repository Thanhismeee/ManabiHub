package com.manabihub.course.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.course.dto.request.CreateCourseDraftRequest;
import com.manabihub.course.dto.response.CourseDraftResponse;
import com.manabihub.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.manabihub.course.dto.response.TeacherCourseAnalyticsResponse;
import com.manabihub.course.dto.response.ValidationResultResponse;
import com.manabihub.course.service.CourseValidationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher/courses")
public class TeacherCourseController {

    private final CourseService courseService;
    private final CourseValidationService courseValidationService;

    @GetMapping("/drafts/{draftId}/validate")
    public ResponseEntity<ApiResponse<ValidationResultResponse>> validateDraft(@PathVariable UUID draftId) {
        ValidationResultResponse response = courseValidationService.validateCourse(draftId);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.MSG_COURSE_001,
                "Course validation result loaded.",
                response
        ));
    }

    @GetMapping("/{courseId}/analytics")
    public ResponseEntity<ApiResponse<TeacherCourseAnalyticsResponse>> getCourseAnalytics(
            @PathVariable UUID courseId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.Instant startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.Instant endDate) {
        TeacherCourseAnalyticsResponse response = courseService.getCourseAnalytics(courseId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Course analytics loaded.",
                response
        ));
    }

    @PostMapping("/drafts/{draftId}/submit-review")
    public ResponseEntity<ApiResponse<Void>> submitForReview(@PathVariable UUID draftId) {
        courseService.submitForReview(draftId);
        return ResponseEntity.ok(ApiResponse.success(
                "MSG-COURSE-005", // MSG-COURSE-005 from SRS: Course submitted for review
                "Sản phẩm đã được gửi để xét duyệt.",
                null
        ));
    }

    @PostMapping("/{courseId}/publish")
    public ResponseEntity<ApiResponse<Void>> publishCourse(@PathVariable UUID courseId) {
        courseService.publishCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.MSG_COURSE_014,
                "Sản phẩm đã được xuất bản và hiển thị trên danh mục.",
                null
        ));
    }

    @PostMapping("/{courseId}/unpublish")
    public ResponseEntity<ApiResponse<CourseDraftResponse>> unpublishCourse(@PathVariable UUID courseId) {
        CourseDraftResponse response = courseService.unpublishCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COURSE_UPDATED,
                "Khóa học đã được ẩn khỏi danh mục. Học viên hiện tại tiếp tục học phiên bản đã duyệt trong khi bạn chỉnh sửa bản mới.",
                response
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseDraftResponse>>> listCourses() {
        List<CourseDraftResponse> response = courseService.listMyCourses();
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Teacher courses loaded.",
                response
        ));
    }

    @GetMapping("/drafts")
    public ResponseEntity<ApiResponse<List<CourseDraftResponse>>> listDrafts() {
        List<CourseDraftResponse> response = courseService.listMyDrafts();

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.MSG_COURSE_001,
                "Course drafts loaded.",
                response
        ));
    }

    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<CourseDraftResponse>> createDraft(
            @Valid @RequestBody CreateCourseDraftRequest request
    ) {
        CourseDraftResponse response = courseService.createDraft(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                MessageCodes.MSG_COURSE_001,
                "Course draft saved.",
                response
        ));
    }

    @PutMapping("/drafts/{draftId}")
    public ResponseEntity<ApiResponse<CourseDraftResponse>> updateDraft(
            @PathVariable UUID draftId,
            @Valid @RequestBody CreateCourseDraftRequest request
    ) {
        CourseDraftResponse response = courseService.updateDraft(draftId, request);

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.MSG_COURSE_001,
                "Course draft updated.",
                response
        ));
    }

    @DeleteMapping("/drafts/{draftId}")
    public ResponseEntity<ApiResponse<Void>> deleteDraft(@PathVariable UUID draftId) {
        courseService.deleteDraft(draftId);

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.MSG_COURSE_001,
                "Course draft deleted.",
                null
        ));
    }
}
