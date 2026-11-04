package com.manabihub.writing.controller;

import com.manabihub.common.response.PageResponse;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.manabihub.writing.dto.response.WritingReviewFacetResponse;
import com.manabihub.writing.dto.response.WritingReviewOverviewResponse;
import com.manabihub.writing.dto.response.WritingSubmissionDetailResponse;
import com.manabihub.writing.dto.response.WritingSubmissionSummaryResponse;
import com.manabihub.writing.enums.WritingSubmissionStatus;
import com.manabihub.writing.service.TeacherWritingReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherWritingReviewController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class TeacherWritingReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherWritingReviewService teacherWritingReviewService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void listSubmissions_asTeacher_returnsPage() throws Exception {
        PageResponse<WritingSubmissionSummaryResponse> page =
                PageResponse.<WritingSubmissionSummaryResponse>builder()
                .content(List.of())
                .page(0)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                        .build();
        when(teacherWritingReviewService.listSubmissions(
                eq(""), eq(null), eq(null), eq(null), eq(null), any()
        ))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/teacher/writing-submissions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getSubmission_asStudent_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/writing-submissions/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listSubmissions_forwardsPaginationAndCourseLessonStatusFilters() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        PageResponse<WritingSubmissionSummaryResponse> page =
                PageResponse.<WritingSubmissionSummaryResponse>builder()
                        .content(List.of())
                        .page(2)
                        .size(5)
                        .totalElements(0)
                        .totalPages(0)
                        .first(false)
                        .last(true)
                        .build();
        when(teacherWritingReviewService.listSubmissions(
                eq("student"), eq(false), eq(courseId), eq(lessonId),
                eq(WritingSubmissionStatus.SUBMITTED), any()
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/teacher/writing-submissions")
                        .param("query", "student")
                        .param("reviewed", "false")
                        .param("courseId", courseId.toString())
                        .param("lessonId", lessonId.toString())
                        .param("status", "SUBMITTED")
                        .param("page", "2")
                        .param("size", "5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5));

        verify(teacherWritingReviewService).listSubmissions(
                eq("student"), eq(false), eq(courseId), eq(lessonId),
                eq(WritingSubmissionStatus.SUBMITTED), any()
        );
    }

    @Test
    void overviewAndFacets_asTeacher_returnAggregateAndOwnedFilterOptions() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        when(teacherWritingReviewService.getOverview("", courseId, null, null))
                .thenReturn(new WritingReviewOverviewResponse(
                        10, 3, 7, new BigDecimal("8.50")
                ));
        when(teacherWritingReviewService.getFacets())
                .thenReturn(new WritingReviewFacetResponse(List.of(
                        new WritingReviewFacetResponse.CourseOption(
                                courseId,
                                "N3 Writing",
                                List.of(new WritingReviewFacetResponse.LessonOption(
                                        lessonId, "Opinion essay"
                                ))
                        )
                )));

        mockMvc.perform(get("/api/v1/teacher/writing-submissions/overview")
                        .param("courseId", courseId.toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSubmissions").value(10))
                .andExpect(jsonPath("$.data.pendingSubmissions").value(3))
                .andExpect(jsonPath("$.data.reviewedSubmissions").value(7))
                .andExpect(jsonPath("$.data.averageScore").value(8.5));

        mockMvc.perform(get("/api/v1/teacher/writing-submissions/facets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[0].id").value(courseId.toString()))
                .andExpect(jsonPath("$.data.courses[0].lessons[0].id")
                        .value(lessonId.toString()));
    }

    @Test
    void saveFeedback_withBlankComment_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/teacher/writing-submissions/{id}/feedback", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                        .contentType("application/json")
                        .content("{\"score\":8.5,\"comment\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value("VALIDATION_FAILED"));
    }

    @Test
    void saveFeedback_withoutScore_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/teacher/writing-submissions/{id}/feedback", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                        .contentType("application/json")
                        .content("{\"comment\":\"Good revision.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value("VALIDATION_FAILED"));
    }

    @Test
    void saveFeedback_asTeacher_returnsUpdatedDetail() throws Exception {
        UUID submissionId = UUID.randomUUID();
        WritingSubmissionDetailResponse response = new WritingSubmissionDetailResponse(
                submissionId,
                UUID.randomUUID(),
                "N3 Writing",
                UUID.randomUUID(),
                "Self introduction",
                "Student A",
                "student@example.com",
                "Japanese writing",
                WritingSubmissionStatus.TEACHER_FEEDBACK_READY,
                Instant.now(),
                null,
                null
        );
        when(teacherWritingReviewService.saveFeedback(eq(submissionId), any()))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/teacher/writing-submissions/{id}/feedback", submissionId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                        .contentType("application/json")
                        .content("{\"score\":8.5,\"comment\":\"Good revision.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("TEACHER_FEEDBACK_SUBMITTED"))
                .andExpect(jsonPath("$.data.id").value(submissionId.toString()));
    }
}
