package com.manabihub.ai.controller;

import com.manabihub.ai.dto.response.AiChatEligibilityResponse;
import com.manabihub.ai.service.AiChatService;
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

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAiChatController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class StudentAiChatControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiChatService aiChatService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void getEligibility_asStudent_returnsEligibility() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID lessonBlockId = UUID.randomUUID();
        when(aiChatService.getEligibility(courseId, lessonBlockId))
                .thenReturn(new AiChatEligibilityResponse(true, null, "AI chat is available."));

        mockMvc.perform(get(eligibilityUrl(courseId, lessonBlockId))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eligible").value(true));
    }

    @Test
    void getEligibility_asTeacher_returnsForbidden() throws Exception {
        mockMvc.perform(get(eligibilityUrl(UUID.randomUUID(), UUID.randomUUID()))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEligibility_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(eligibilityUrl(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessage_withBlankQuestion_returnsBadRequest() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID lessonBlockId = UUID.randomUUID();

        mockMvc.perform(post(messagesUrl(courseId, lessonBlockId))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
                        .content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value("VALIDATION_FAILED"));
    }

    private String eligibilityUrl(UUID courseId, UUID lessonBlockId) {
        return "/api/v1/student/courses/%s/lesson-blocks/%s/ai-chat/eligibility"
                .formatted(courseId, lessonBlockId);
    }

    private String messagesUrl(UUID courseId, UUID lessonBlockId) {
        return "/api/v1/student/courses/%s/lesson-blocks/%s/ai-chat/messages"
                .formatted(courseId, lessonBlockId);
    }
}
