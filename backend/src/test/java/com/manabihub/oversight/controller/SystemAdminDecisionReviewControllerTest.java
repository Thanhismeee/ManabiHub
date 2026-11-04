package com.manabihub.oversight.controller;

import com.manabihub.oversight.service.OperationalDecisionReviewService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MHB-026: hai thao tac ghi cua man hinh hau kiem phai tra ve ma thong diep
 * rieng chu khong phai ma chung COMMON_SUCCESS. Kiem o tang controller vi loi
 * nam dung o cho goi ApiResponse.success(data); test tang service khong thay duoc.
 */
@WebMvcTest(SystemAdminDecisionReviewController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class SystemAdminDecisionReviewControllerTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID AUDIT_LOG_ID =
            UUID.fromString("0b0760fd-cab5-404d-b484-127cf263689a");

    @Autowired private MockMvc mockMvc;

    @MockBean private OperationalDecisionReviewService reviewService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void markingReviewedReturnsAnAdminActionCodeRatherThanTheGenericSuccessCode() throws Exception {
        mockMvc.perform(post("/api/v1/admin/decision-reviews/" + AUDIT_LOG_ID + "/reviewed")
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messageCode").value("ADMIN_ACTION_SUCCESS"));
    }

    @Test
    void sendingAWarningReturnsTheOversightMessageCode() throws Exception {
        mockMvc.perform(post("/api/v1/admin/decision-reviews/" + AUDIT_LOG_ID + "/warnings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"WARNING\",\"note\":\"kiem tra ma thong diep\"}")
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messageCode").value("MSG-OVS-001"));
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