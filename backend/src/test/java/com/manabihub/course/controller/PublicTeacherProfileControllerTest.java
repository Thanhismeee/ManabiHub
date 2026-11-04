package com.manabihub.course.controller;

import com.manabihub.course.dto.response.PublicTeacherCourseResponse;
import com.manabihub.course.dto.response.PublicTeacherCredentialResponse;
import com.manabihub.course.dto.response.PublicTeacherProfileResponse;
import com.manabihub.course.dto.response.PublicTeacherRatingSummaryResponse;
import com.manabihub.course.dto.response.PublicTeacherSummaryResponse;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.service.PublicTeacherProfileService;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicTeacherProfileController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class PublicTeacherProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicTeacherProfileService publicTeacherProfileService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void getProfile_IsPublicAndSerializesOnlyPublicSafeFields() throws Exception {
        UUID teacherId = UUID.randomUUID();
        PublicTeacherCourseResponse course = new PublicTeacherCourseResponse(
                UUID.randomUUID(),
                "N5 Foundations",
                "n5-foundations",
                "/thumbnail.png",
                JlptLevel.N5,
                "Grammar",
                new BigDecimal("299000"),
                "VND",
                12,
                Instant.parse("2026-07-01T00:00:00Z"),
                new BigDecimal("4.8"),
                16
        );
        when(publicTeacherProfileService.getProfile(teacherId))
                .thenReturn(new PublicTeacherProfileResponse(
                        teacherId,
                        "Sensei An",
                        "/avatar.png",
                        "N5 grammar teacher",
                        true,
                        1,
                        List.of(new PublicTeacherCredentialResponse("JLPT", "N5", "APPROVED")),
                        new PublicTeacherRatingSummaryResponse(new BigDecimal("4.8"), 16),
                        List.of(course)
                ));

        mockMvc.perform(get("/api/v1/public/teachers/{teacherId}", teacherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(teacherId.toString())))
                .andExpect(jsonPath("$.data.displayName", is("Sensei An")))
                .andExpect(jsonPath("$.data.verified", is(true)))
                .andExpect(jsonPath("$.data.credentials[0].type", is("JLPT")))
                .andExpect(jsonPath("$.data.credentials[0].level", is("N5")))
                .andExpect(jsonPath("$.data.credentials[0].verificationStatus", is("APPROVED")))
                .andExpect(jsonPath("$.data.ratingSummary.averageRating", is(4.8)))
                .andExpect(jsonPath("$.data.ratingSummary.reviewCount", is(16)))
                .andExpect(jsonPath("$.data.courses", hasSize(1)))
                .andExpect(jsonPath("$.data.courses[0].slug", is("n5-foundations")))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.kycStatus").doesNotExist())
                .andExpect(jsonPath("$.data.canPublishCourse").doesNotExist())
                .andExpect(jsonPath("$.data.kycDocuments").doesNotExist());
    }

    @Test
    void listFeatured_IsPublicAndHonorsBoundedLimit() throws Exception {
        UUID teacherId = UUID.randomUUID();
        when(publicTeacherProfileService.listFeatured(3)).thenReturn(List.of(
                new PublicTeacherSummaryResponse(
                        teacherId,
                        "Sensei An",
                        null,
                        "N5 grammar teacher",
                        true,
                        2
                )
        ));

        mockMvc.perform(get("/api/v1/public/teachers").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(teacherId.toString())))
                .andExpect(jsonPath("$.data[0].publishedCourseCount", is(2)));
    }

    @Test
    void listFeatured_WhenLimitExceedsMaximum_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/public/teachers").param("limit", "13"))
                .andExpect(status().isBadRequest());

        verify(publicTeacherProfileService, never()).listFeatured(13);
    }
}
