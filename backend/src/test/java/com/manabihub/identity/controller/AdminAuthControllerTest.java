package com.manabihub.identity.controller;

import com.manabihub.identity.service.AdminAuthService;
import com.manabihub.identity.service.AdminSessionBundle;
import com.manabihub.identity.service.AdminPasswordResetService;
import com.manabihub.identity.service.AdminRefreshCookieService;
import com.manabihub.identity.service.InternalAdminInvitationService;
import com.manabihub.identity.service.InternalAdminSessionService;
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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuthController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class AdminAuthControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AdminAuthService adminAuthService;
    @MockBean private InternalAdminInvitationService invitationService;
    @MockBean private InternalAdminSessionService sessionService;
    @MockBean private AdminPasswordResetService passwordResetService;
    @MockBean private AdminRefreshCookieService refreshCookieService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void loginReturnsShortLivedAccessCredentialsAndWritesRefreshCookie()
            throws Exception {
        AdminSessionBundle bundle = new AdminSessionBundle(
                "access-token",
                "refresh-token",
                "csrf-token",
                true,
                Instant.parse("2026-08-29T10:00:00Z")
        );
        when(adminAuthService.login(any(), eq("127.0.0.1"), eq("JUnit")))
                .thenReturn(bundle);

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType("application/json")
                        .header("User-Agent", "JUnit")
                        .content("""
                                {
                                  "email":"admin@example.com",
                                  "password":"StrongPassword!42",
                                  "rememberMe":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("access-token"))
                .andExpect(jsonPath("$.data.csrfToken").value("csrf-token"))
                .andExpect(jsonPath("$.data.remembered").value(true));

        verify(refreshCookieService).write(
                any(),
                eq("refresh-token"),
                eq(true),
                eq(Instant.parse("2026-08-29T10:00:00Z"))
        );
    }

    @Test
    void forgotPasswordAlwaysReturnsGenericAcceptedResponse() throws Exception {
        mockMvc.perform(post("/api/admin/auth/password/forgot")
                        .contentType("application/json")
                        .header("User-Agent", "JUnit")
                        .content("""
                                {"email":"unknown@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode")
                        .value("ADMIN_PASSWORD_RESET_REQUEST_ACCEPTED"));

        verify(passwordResetService).request(
                "unknown@example.com",
                "127.0.0.1",
                "JUnit"
        );
    }

    @Test
    void refreshRequiresDoubleSubmitCsrfHeader() throws Exception {
        mockMvc.perform(post("/api/admin/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                AdminRefreshCookieService.DEFAULT_COOKIE_NAME,
                                "refresh-token"
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousRecipientCanSetPasswordWithInvitationToken() throws Exception {
        mockMvc.perform(post("/api/admin/auth/setup-password")
                        .contentType("application/json")
                        .header("User-Agent", "JUnit")
                        .content("""
                                {
                                  "token":"valid-one-time-token",
                                  "password":"StrongPassword!42"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode")
                        .value("INTERNAL_ADMIN_PASSWORD_SET"));

        verify(invitationService).accept(
                "valid-one-time-token",
                "StrongPassword!42",
                "127.0.0.1",
                "JUnit"
        );
    }

    @Test
    void setupPasswordRejectsMissingTokenBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/admin/auth/setup-password")
                        .contentType("application/json")
                        .content("""
                                {"token":" ","password":"StrongPassword!42"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value("VALIDATION_FAILED"));
    }
}
