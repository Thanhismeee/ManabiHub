package com.manabihub.kyc.controller;

import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.service.TeacherKycService;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherKycController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, com.manabihub.common.exception.GlobalExceptionHandler.class, com.manabihub.security.DummyFilterConfig.class})
class TeacherKycControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherKycService teacherKycService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;

    @MockBean
    private OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    @Test
    void statusRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/kyc/status"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(currentUserService, teacherKycService);
    }

    @Test
    void statusUsesAuthenticatedIdentityAndIgnoresDemoHeader() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID forgedUserId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(authenticatedUserId);

        mockMvc.perform(get("/api/v1/teacher/kyc/status")
                        .header("X-Demo-User-Id", forgedUserId)
                        .with(jwt().jwt(jwt -> jwt.subject(authenticatedUserId.toString()))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk());

        verify(teacherKycService).getStatus(authenticatedUserId);
        verify(teacherKycService, never()).getStatus(forgedUserId);
    }

    @Test
    void verifyIdentity_Returns409Conflict_WhenDuplicateCccdDetected() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(authenticatedUserId);
        when(teacherKycService.verifyIdentity(org.mockito.ArgumentMatchers.eq(authenticatedUserId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.manabihub.common.exception.BusinessException(
                        com.manabihub.common.constants.MessageCodes.MSG_KYC_008,
                        "Số CCCD này đã được sử dụng bởi một tài khoản giáo viên khác",
                        org.springframework.http.HttpStatus.CONFLICT
                ));

        String requestJson = """
                {
                    "providerSessionId": "sess-1",
                    "providerTransactionId": "tx-1",
                    "sdkResult": {
                        "idNumber": "012345678901",
                        "fullName": "Nguyen Van A"
                    }
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/teacher/kyc/identity-verifications")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(jwt().jwt(jwt -> jwt.subject(authenticatedUserId.toString()))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.messageCode").value("MSG-KYC-008"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("Số CCCD này đã được sử dụng bởi một tài khoản giáo viên khác"));
    }
}
