package com.manabihub.course.controller;

import com.manabihub.course.dto.response.TeacherDashboardResponse;
import com.manabihub.course.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherDashboardController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
@ActiveProfiles("test")
public class TeacherDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private CourseService courseService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @org.springframework.boot.test.mock.mockito.MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    @WithMockUser(roles = "TEACHER")
    public void getDashboardStats_asTeacher_shouldReturn200() throws Exception {
        TeacherDashboardResponse mockResponse = TeacherDashboardResponse.builder().build();
        when(courseService.getTeacherDashboardStats()).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/teacher/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    public void getDashboardStats_asStudent_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void getDashboardStats_asAnonymous_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}
