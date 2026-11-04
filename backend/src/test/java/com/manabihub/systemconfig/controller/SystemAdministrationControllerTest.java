package com.manabihub.systemconfig.controller;

import com.manabihub.security.DummyFilterConfig;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.manabihub.systemconfig.dto.response.SystemSettingResponse;
import com.manabihub.systemconfig.service.SystemAdministrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemAdministrationController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class SystemAdministrationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private SystemAdministrationService administrationService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void systemAdminCanReadSettings() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(administrationService.listSettings(actorId)).thenReturn(List.of(
                new SystemSettingResponse(
                        UUID.randomUUID(),
                        "COMMISSION_RATE",
                        "0.20",
                        "NUMBER",
                        "Commission",
                        true,
                        null,
                        null
                )
        ));

        mockMvc.perform(get("/api/v1/admin/system-settings")
                        .with(adminJwt(actorId, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("COMMISSION_RATE"))
                .andExpect(jsonPath("$.data[0].value").value("0.20"));
    }

    @Test
    void courseManagerCannotReadOrUpdateSettings() throws Exception {
        UUID actorId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/system-settings")
                        .with(adminJwt(actorId, "COURSE_MANAGER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/system-settings/COMMISSION_RATE")
                        .with(adminJwt(actorId, "COURSE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"value":"0.25","reason":"not allowed"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRequiresReasonAndReturnsCanonicalCode() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(administrationService.updateSetting(
                eq(actorId),
                eq("COMMISSION_RATE"),
                eq("0.25"),
                eq("Council-approved pricing")
        )).thenReturn(new SystemSettingResponse(
                UUID.randomUUID(),
                "COMMISSION_RATE",
                "0.25",
                "NUMBER",
                "Commission",
                true,
                actorId,
                null
        ));

        mockMvc.perform(put("/api/v1/admin/system-settings/COMMISSION_RATE")
                        .with(adminJwt(actorId, "SYSTEM_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"value":"0.25","reason":"Council-approved pricing"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("SYSTEM_SETTING_UPDATED"))
                .andExpect(jsonPath("$.data.value").value("0.25"));

        mockMvc.perform(put("/api/v1/admin/system-settings/COMMISSION_RATE")
                        .with(adminJwt(actorId, "SYSTEM_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"value":"0.25","reason":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value("VALIDATION_FAILED"));
    }

    @Test
    void anonymousCannotReadSettings() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system-settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlySystemAdminCanCreateInternalAccountInvitation() throws Exception {
        UUID actorId = UUID.randomUUID();
        String body = """
                {
                  "email":"manager@example.com",
                  "fullName":"Course Manager",
                  "roleCode":"COURSE_MANAGER",
                  "reason":"Approved staffing request"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/internal-accounts/invitations")
                        .with(adminJwt(actorId, "SYSTEM_ADMIN"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode")
                        .value("INTERNAL_ADMIN_INVITATION_CREATED"));
        verify(administrationService).inviteInternalAdmin(
                actorId,
                "manager@example.com",
                "Course Manager",
                com.manabihub.identity.enums.RoleCode.COURSE_MANAGER,
                "Approved staffing request"
        );

        mockMvc.perform(post("/api/v1/admin/internal-accounts/invitations")
                        .with(adminJwt(UUID.randomUUID(), "COURSE_MANAGER"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlySystemAdminCanDisableAnInternalAccountWithAReason() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/admin/internal-accounts/{adminId}/status", targetId)
                        .with(adminJwt(actorId, "SYSTEM_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"status":"DISABLED","reason":"Staff access revoked"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode")
                        .value("INTERNAL_ADMIN_STATUS_UPDATED"));
        verify(administrationService).updateInternalAdminStatus(
                actorId,
                targetId,
                com.manabihub.identity.enums.AccountStatus.DISABLED,
                "Staff access revoked"
        );

        mockMvc.perform(patch("/api/v1/admin/internal-accounts/{adminId}/status", targetId)
                        .with(adminJwt(actorId, "SYSTEM_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"status":"DISABLED","reason":"no"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value("VALIDATION_FAILED"));

        mockMvc.perform(patch("/api/v1/admin/internal-accounts/{adminId}/status", targetId)
                        .with(adminJwt(UUID.randomUUID(), "COURSE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"status":"DISABLED","reason":"Not authorized"}
                                """))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt(
            UUID id,
            String role
    ) {
        return jwt()
                .jwt(token -> token.subject(id.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
