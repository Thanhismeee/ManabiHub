package com.manabihub.learning.controller;

import com.manabihub.learning.dto.response.LearningCertificateResponse;
import com.manabihub.learning.service.StudentCertificateService;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentCertificateController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class StudentCertificateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentCertificateService certificateService;
    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void generateCertificate_asStudentReturnsIssuedRecord() throws Exception {
        UUID courseId = UUID.randomUUID();
        when(certificateService.generateCertificate(courseId)).thenReturn(response(courseId));

        mockMvc.perform(post("/api/v1/student/courses/{courseId}/certificate", courseId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode", is("LEARNING_CERTIFICATE_ISSUED")))
                .andExpect(jsonPath("$.data.courseId", is(courseId.toString())))
                .andExpect(jsonPath("$.data.certificateNumber", is("MHB-TEST")))
                .andExpect(jsonPath("$.data.issuedAt", is("2026-07-24T00:00:00Z")))
                .andExpect(jsonPath("$.data.completedAt", is("2026-07-23T23:59:58Z")));
    }

    @Test
    void getCertificate_asStudentReturnsOwnedRecord() throws Exception {
        UUID courseId = UUID.randomUUID();
        when(certificateService.getCertificate(courseId)).thenReturn(response(courseId));

        mockMvc.perform(get("/api/v1/student/courses/{courseId}/certificate", courseId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentName", is("An Nguyen")));
    }

    @Test
    void certificateEndpoint_rejectsWrongRole() throws Exception {
        mockMvc.perform(post("/api/v1/student/courses/{courseId}/certificate", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(certificateService);
    }

    @Test
    void certificateEndpoint_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/student/courses/{courseId}/certificate", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private LearningCertificateResponse response(UUID courseId) {
        return new LearningCertificateResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                courseId,
                "MHB-TEST",
                "An Nguyen",
                "Japanese Foundations",
                Instant.parse("2026-07-24T00:00:00Z"),
                Instant.parse("2026-07-23T23:59:58Z")
        );
    }
}
