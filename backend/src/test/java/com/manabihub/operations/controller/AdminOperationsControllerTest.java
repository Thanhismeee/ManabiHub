package com.manabihub.operations.controller;

import com.manabihub.operations.dto.OperationsOverviewResponse;
import com.manabihub.operations.dto.OperationsOverviewResponse.BuildSnapshot;
import com.manabihub.operations.dto.OperationsOverviewResponse.MemorySnapshot;
import com.manabihub.operations.dto.OperationsOverviewResponse.ThreadSnapshot;
import com.manabihub.operations.dto.RecentApplicationLogResponse;
import com.manabihub.operations.dto.RuntimeConfigurationResponse;
import com.manabihub.operations.service.OperationsService;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOperationsController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class AdminOperationsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OperationsService operationsService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;
    @Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void systemAdminCanReadOverview() throws Exception {
        when(operationsService.getOverview()).thenReturn(new OperationsOverviewResponse(
                "UP", "UP", Instant.parse("2026-08-26T10:00:00Z"), 120,
                List.of("prod"), "UTC", "Asia/Ho_Chi_Minh", "21", "OpenJDK",
                new MemorySnapshot(10, 20, 30, 5),
                new ThreadSnapshot(8, 6, 10),
                new BuildSnapshot("manabihub-backend", "1.0", null, "abc123")
        ));

        mockMvc.perform(get("/api/v1/admin/operations/overview")
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("COMMON_SUCCESS"))
                .andExpect(jsonPath("$.data.applicationStatus").value("UP"))
                .andExpect(jsonPath("$.data.databaseStatus").value("UP"))
                .andExpect(jsonPath("$.data.timezone").value("UTC"))
                .andExpect(jsonPath("$.data.businessTimezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.memory.heapMaxBytes").value(30));
    }

    @Test
    void systemAdminCanReadAllowlistedRuntimeConfiguration() throws Exception {
        when(operationsService.getRuntimeConfiguration()).thenReturn(
                new RuntimeConfigurationResponse(List.of(
                        new RuntimeConfigurationResponse.ComponentStatus("DATABASE", true, true)
                ))
        );

        mockMvc.perform(get("/api/v1/admin/operations/runtime-config")
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components[0].component").value("DATABASE"))
                .andExpect(jsonPath("$.data.components[0].configured").value(true));
    }

    @Test
    void systemAdminCanFilterBoundedCurrentInstanceLogs() throws Exception {
        RecentApplicationLogResponse response = new RecentApplicationLogResponse(
                "CURRENT_INSTANCE_MEMORY", Instant.parse("2026-08-26T10:00:00Z"),
                500, 0, List.of()
        );
        when(operationsService.getRecentLogs(
                eq("ERROR"), eq("payment"), eq("req-1"), eq(25)
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/operations/logs")
                        .param("level", "ERROR")
                        .param("query", "payment")
                        .param("correlationId", "req-1")
                        .param("limit", "25")
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logSource").value("CURRENT_INSTANCE_MEMORY"))
                .andExpect(jsonPath("$.data.capacity").value(500))
                .andExpect(jsonPath("$.data.returned").value(0));
    }

    @Test
    void rejectsInvalidLogFilters() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/logs")
                        .param("level", "FATAL")
                        .param("limit", "201")
                        .with(adminJwt("SYSTEM_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonSystemAdminAndAnonymousCannotReadOperations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/overview")
                        .with(adminJwt("FINANCE_MANAGER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/operations/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operationsSurfaceHasNoMutationEndpoint() {
        Set<RequestMethod> operationsMethods = requestMappingHandlerMapping.getHandlerMethods()
                .keySet()
                .stream()
                .filter(mapping -> mapping.getPatternValues().stream()
                        .anyMatch(path -> path.startsWith("/api/v1/admin/operations")))
                .flatMap(mapping -> mapping.getMethodsCondition().getMethods().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(operationsMethods).containsExactly(RequestMethod.GET);
    }

    private org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor adminJwt(String role) {
        return jwt()
                .jwt(builder -> builder.subject("c0000000-0000-0000-0000-000000000001"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
