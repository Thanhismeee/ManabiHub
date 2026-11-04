package com.manabihub.admin.controller;

import com.manabihub.audit.dto.AuditLogDetailDto;
import com.manabihub.audit.dto.AuditLogFilterDto;
import com.manabihub.audit.service.AuditLogService;
import com.manabihub.audit.service.AccessDenialAuditService;
import com.manabihub.common.response.PageResponse;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuditLogController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class AdminAuditLogControllerTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Autowired private MockMvc mockMvc;

    @MockBean private AuditLogService auditLogService;
    @MockBean private AccessDenialAuditService accessDenialAuditService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void systemAdminCanReadAuditLogList() throws Exception {
        when(auditLogService.getAuditLogs(any(AuditLogFilterDto.class), any()))
                .thenReturn(PageResponse.<com.manabihub.audit.dto.AuditLogDto>builder()
                        .content(List.of())
                        .totalElements(42)
                        .page(2)
                        .size(20)
                        .totalPages(3)
                        .build());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("page", "2")
                        .param("size", "20")
                        .param("sort", "createdAt,desc")
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("COMMON_SUCCESS"))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(42));
    }

    @Test
    void systemAdminCanReadAuditLogDetail() throws Exception {
        UUID auditLogId = UUID.randomUUID();
        when(auditLogService.getAuditLogDetail(auditLogId))
                .thenReturn(AuditLogDetailDto.builder().id(auditLogId).build());

        mockMvc.perform(get("/api/v1/admin/audit-logs/" + auditLogId)
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(auditLogId.toString()));
    }

    @Test
    void courseManagerCannotReadAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .with(adminJwt("COURSE_MANAGER")))
                .andExpect(status().isForbidden());
        verify(accessDenialAuditService).record(any(), eq("MSG-ADM-006"));
    }

    @Test
    void anonymousUserCannotReadAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * MHB-025 / NFR-SEC-35: nhat ky he thong chi doc. Kiem qua MockMvc chu khong
     * goi thang ham xu ly ngoai le, vi loi nam o cho bo bat-tat-ca gianh mat
     * ngoai le trong qua trinh dieu phoi - goi thang se xanh gia.
     */
    @Test
    void deletingAnAuditLogIsRejectedWithMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/audit-logs/" + UUID.randomUUID())
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.messageCode").value("COMMON_METHOD_NOT_ALLOWED"));
    }

    @Test
    void creatingAnAuditLogIsRejectedWithMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/admin/audit-logs")
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.messageCode").value("COMMON_METHOD_NOT_ALLOWED"));
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
