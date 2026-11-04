package com.manabihub.course.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.course.service.CourseService;
import com.manabihub.course.service.CourseValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeacherCourseControllerTest {

    @Mock
    private CourseService courseService;

    @Mock
    private CourseValidationService courseValidationService;

    @InjectMocks
    private TeacherCourseController controller;

    @Test
    void publishCourse_ShouldReturnSrsSuccessMessage() {
        UUID courseId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response = controller.publishCourse(courseId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(MessageCodes.MSG_COURSE_014, response.getBody().getMessageCode());
        assertEquals(
                "Sản phẩm đã được xuất bản và hiển thị trên danh mục.",
                response.getBody().getMessage()
        );
        verify(courseService).publishCourse(courseId);
    }

    @Test
    void getCourseAnalytics_ShouldReturnAnalyticsData() {
        UUID courseId = UUID.randomUUID();
        java.time.Instant startDate = java.time.Instant.now().minus(java.time.Duration.ofDays(30));
        java.time.Instant endDate = java.time.Instant.now();
        
        com.manabihub.course.dto.response.TeacherCourseAnalyticsResponse mockResponse = com.manabihub.course.dto.response.TeacherCourseAnalyticsResponse.builder()
                .totalEnrollment(100)
                .completionRate(75.5)
                .grossRevenue(java.math.BigDecimal.valueOf(1000000))
                .netRevenue(java.math.BigDecimal.valueOf(800000))
                .refundRate(5.0)
                .build();
                
        org.mockito.Mockito.when(courseService.getCourseAnalytics(courseId, startDate, endDate)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<com.manabihub.course.dto.response.TeacherCourseAnalyticsResponse>> response = controller.getCourseAnalytics(courseId, startDate, endDate);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(MessageCodes.COMMON_SUCCESS, response.getBody().getMessageCode());
        assertEquals(100, response.getBody().getData().getTotalEnrollment());
        assertEquals(75.5, response.getBody().getData().getCompletionRate());
        assertEquals(5.0, response.getBody().getData().getRefundRate());
        
        verify(courseService).getCourseAnalytics(courseId, startDate, endDate);
    }
}
