package com.manabihub.review.controller;

import com.manabihub.review.dto.request.UpsertCourseReviewRequest;
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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentCourseReviewController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class StudentCourseReviewControllerTest {

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
    void upsert_asStudentUsesOwnerScopedCourseRoute() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        CourseReviewResponse response = new CourseReviewResponse(
                reviewId,
                5,
                "Nội dung thực tế và dễ hiểu.",
                "Học viên An",
                null,
                Instant.parse("2026-07-27T00:00:00Z")
        );
        when(courseReviewService.upsertMyReview(
                eq(courseId),
                any(UpsertCourseReviewRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/v1/student/courses/{courseId}/review", courseId)
                        .with(studentJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "reviewText": "Nội dung thực tế và dễ hiểu."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode", is("COURSE_REVIEW_SAVED")))
                .andExpect(jsonPath("$.data.id", is(reviewId.toString())))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.studentId").doesNotExist());
    }

    @Test
    void upsert_rejectsOutOfRangeRatingAndShortText() throws Exception {
        mockMvc.perform(put(
                        "/api/v1/student/courses/{courseId}/review",
                        UUID.randomUUID()
                )
                        .with(studentJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 6,
                                  "reviewText": "ngắn"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode", is("VALIDATION_FAILED")));

        verifyNoInteractions(courseReviewService);
    }

    @Test
    void reviewRoute_rejectsTeacherAndAnonymousUsers() throws Exception {
        UUID courseId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/student/courses/{courseId}/review", courseId)
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_TEACHER")
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/student/courses/{courseId}/review", courseId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(courseReviewService);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor studentJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}
