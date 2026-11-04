package com.manabihub.refund.controller;

import com.manabihub.common.response.PageResponse;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.refund.dto.request.CreateStudentRefundRequest;
import com.manabihub.refund.service.StudentRefundService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentRefundController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class StudentRefundControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private StudentRefundService studentRefundService;
    @MockBean private CurrentUserService currentUserService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void create_asStudentUsesAuthenticatedUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(userId);

        mockMvc.perform(post("/api/v1/student/refunds")
                        .with(studentJwt())
                        .contentType("application/json")
                        .content("""
                                {"orderItemId":"%s","refundType":"STANDARD","reason":"Course is not suitable"}
                                """.formatted(orderItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("REFUND_REQUESTED"));

        verify(studentRefundService).createRefundRequest(
                eq(userId),
                any(CreateStudentRefundRequest.class)
        );
    }

    @Test
    void list_asStudentReturnsOwnedPage() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(studentRefundService.getMyRefundRequests(eq(userId), any()))
                .thenReturn(PageResponse.<com.manabihub.refund.dto.response.StudentRefundResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .first(true)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/v1/student/refunds").with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void endpointsRejectTeacherRole() throws Exception {
        mockMvc.perform(get("/api/v1/student/refunds")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(studentRefundService);
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/student/refunds"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor studentJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}
