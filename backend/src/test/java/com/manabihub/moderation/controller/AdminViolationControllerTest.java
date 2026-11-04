package com.manabihub.moderation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.moderation.dto.request.ResolveViolationRequest;
import com.manabihub.moderation.enums.ModerationDecisionType;
import com.manabihub.moderation.service.ViolationModerationService;
import com.manabihub.security.DummyFilterConfig;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminViolationController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class AdminViolationControllerTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("c0000000-0000-0000-0000-000000000002");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ViolationModerationService violationModerationService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void courseManagerCanReadViolationQueue() throws Exception {
        when(violationModerationService.getViolationQueue(any(), any(), eq(ADMIN_ID)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/violations")
                        .with(adminJwt("COURSE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("COMMON_SUCCESS"));

        verify(violationModerationService)
                .getViolationQueue(any(), any(), eq(ADMIN_ID));
    }

    @Test
    void courseManagerCanSubmitDismissDecision() throws Exception {
        ResolveViolationRequest request = new ResolveViolationRequest();
        request.setDecision(ModerationDecisionType.DISMISSED);
        request.setDecisionNote("The submitted evidence does not establish a violation.");
        request.setActions(List.of());
        UUID reportId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/violations/" + reportId + "/resolve")
                        .with(adminJwt("COURSE_MANAGER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("MSG-ADM-003"));

        verify(violationModerationService)
                .resolveViolation(eq(reportId), any(ResolveViolationRequest.class), eq(ADMIN_ID));
    }

    @Test
    void courseManagerCanDownloadPrivateViolationEvidence() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        byte[] fileContent = new byte[]{1, 2, 3, 4};
        when(violationModerationService.getViolationEvidence(reportId, evidenceId, ADMIN_ID))
                .thenReturn(new ViolationModerationService.ViolationEvidenceDownload(
                        "bang-chung.png",
                        "image/png",
                        new ByteArrayResource(fileContent)
                ));

        mockMvc.perform(get("/api/v1/admin/violations/" + reportId + "/evidence/" + evidenceId)
                        .with(adminJwt("COURSE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(fileContent));

        verify(violationModerationService).getViolationEvidence(reportId, evidenceId, ADMIN_ID);
    }

    @Test
    void financeManagerCannotAccessViolationModeration() throws Exception {
        mockMvc.perform(get("/api/v1/admin/violations")
                        .with(adminJwt("FINANCE_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotAccessViolationModeration() throws Exception {
        mockMvc.perform(get("/api/v1/admin/violations")
                        .with(jwt()
                                .jwt(builder -> builder.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCannotAccessViolationModeration() throws Exception {
        mockMvc.perform(get("/api/v1/admin/violations"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor adminJwt(
                    String role
            ) {
        return jwt()
                .jwt(builder -> builder.subject(ADMIN_ID.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
