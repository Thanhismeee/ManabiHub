package com.manabihub.review.controller;

import com.manabihub.review.dto.request.TeacherCourseReviewReplyRequest;
import com.manabihub.review.dto.response.CourseReviewResponse;
import com.manabihub.review.service.CourseReviewService;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherCourseReviewController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class TeacherCourseReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseReviewService courseReviewService;
    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void reply_asTeacher_returnsUpdatedReview() throws Exception {
        UUID reviewId = UUID.randomUUID();
        CourseReviewResponse response = new CourseReviewResponse(
                reviewId,
                5,
                "Khóa học rất hữu ích.",
                "Học viên An",
                null,
                Instant.parse("2026-08-10T10:00:00Z"),
                "Cảm ơn em đã góp ý.",
                Instant.parse("2026-08-10T10:05:00Z")
        );
        when(courseReviewService.replyToReview(
                eq(reviewId),
                any(TeacherCourseReviewReplyRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/v1/teacher/course-reviews/{reviewId}/reply", reviewId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replyText\":\"Cảm ơn em đã góp ý.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reviewId.toString()))
                .andExpect(jsonPath("$.data.teacherReplyText").value("Cảm ơn em đã góp ý."));
    }

    @Test
    void reply_rejectsStudentAndBlankContent() throws Exception {
        UUID reviewId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/teacher/course-reviews/{reviewId}/reply", reviewId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replyText\":\"Không được phép\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/teacher/course-reviews/{reviewId}/reply", reviewId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replyText\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courseReviewService);
    }
}
