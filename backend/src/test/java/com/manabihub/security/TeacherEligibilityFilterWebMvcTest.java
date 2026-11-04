package com.manabihub.security;

import com.manabihub.common.response.PageResponse;
import com.manabihub.course.controller.TeacherDashboardController;
import com.manabihub.course.dto.response.TeacherDashboardResponse;
import com.manabihub.course.service.CourseService;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.controller.TeacherKycController;
import com.manabihub.kyc.service.TeacherKycService;
import com.manabihub.payout.controller.TeacherWithdrawalController;
import com.manabihub.payout.service.WithdrawalService;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.config.InternalAdminRoleFilter;
import com.manabihub.security.config.TeacherEligibilityFilter;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.manabihub.wallet.controller.TeacherWalletController;
import com.manabihub.wallet.service.WalletService;
import com.manabihub.wallet.service.EscrowService;
import com.manabihub.wallet.service.TeacherRevenueService;
import com.manabihub.wallet.service.WalletTransactionService;
import com.manabihub.writing.controller.TeacherWritingReviewController;
import com.manabihub.writing.dto.response.WritingSubmissionSummaryResponse;
import com.manabihub.writing.service.TeacherWritingReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc coverage for the HTTP/security behavior of {@link TeacherEligibilityFilter}.
 *
 * This class uses a mocked {@link JdbcTemplate}; PostgreSQL role revocation is
 * covered separately by TeacherIdentityClaimDuplicatePostgresIntegrationTest.
 */
@WebMvcTest({
        TeacherDashboardController.class,
        TeacherWritingReviewController.class,
        TeacherKycController.class,
        TeacherWalletController.class,
        TeacherWithdrawalController.class
})
@Import({SecurityConfig.class, TeacherEligibilityFilter.class})
class TeacherEligibilityFilterWebMvcTest {

    private static final UUID TEACHER_ROLE_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final String COUNT_TEACHER_ROLE_SQL =
            "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_id = ?";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @MockBean
    private TeacherWritingReviewService teacherWritingReviewService;

    @MockBean
    private TeacherKycService teacherKycService;

    @MockBean
    private WalletService walletService;

    @MockBean
    private EscrowService escrowService;

    @MockBean
    private WalletTransactionService walletTransactionService;

    @MockBean
    private TeacherRevenueService teacherRevenueService;

    @MockBean
    private WithdrawalService withdrawalService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private InternalAdminRoleFilter internalAdminRoleFilter;

    @MockBean
    private com.manabihub.identity.service.PublicUserSessionService publicUserSessionService;

    @MockBean
    private com.manabihub.identity.service.LearningSessionLeaseService learningSessionLeaseService;

    @Test
    void activeTeacher_dashboardReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        mockTeacherRoleCount(userId, 1);

        when(courseService.getTeacherDashboardStats())
                .thenReturn(TeacherDashboardResponse.builder()
                        .totalCourses(0)
                        .draftOrCorrection(0)
                        .pendingApproval(0)
                        .published(0)
                        .recentCourses(List.of())
                        .build());

        mockMvc.perform(get("/api/v1/teacher/dashboard")
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("role", "TEACHER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk());
    }

    @Test
    void activeTeacher_writingSubmissionsReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        mockTeacherRoleCount(userId, 1);

        PageResponse<WritingSubmissionSummaryResponse> page =
                PageResponse.<WritingSubmissionSummaryResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .first(true)
                        .last(true)
                        .build();
        when(teacherWritingReviewService.listSubmissions(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/teacher/writing-submissions")
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("role", "TEACHER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk());
    }

    @Test
    void quarantinedTeacher_staleJwt_dashboardReturns403() throws Exception {
        UUID userId = UUID.randomUUID();
        mockTeacherRoleCount(userId, 0);

        mockMvc.perform(get("/api/v1/teacher/dashboard")
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("role", "TEACHER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageCode").value("AUTH_FORBIDDEN"))
                .andExpect(jsonPath("$.message")
                        .value("Teacher access is not available for this account."));
    }

    @Test
    void quarantinedTeacher_staleJwt_writingReviewReturns403() throws Exception {
        UUID userId = UUID.randomUUID();
        mockTeacherRoleCount(userId, 0);

        mockMvc.perform(get("/api/v1/teacher/writing-submissions")
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("role", "TEACHER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageCode").value("AUTH_FORBIDDEN"));
    }

    @Test
    void quarantinedTeacher_staleJwt_financialEndpointsReturn403() throws Exception {
        UUID userId = UUID.randomUUID();
        mockTeacherRoleCount(userId, 0);

        var staleTeacherJwt = jwt()
                .jwt(j -> j.subject(userId.toString()).claim("role", "TEACHER"))
                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"));

        mockMvc.perform(get("/api/v1/teacher/wallet").with(staleTeacherJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageCode").value("AUTH_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/teacher/withdrawals").with(staleTeacherJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageCode").value("AUTH_FORBIDDEN"));

        verifyNoInteractions(walletService, withdrawalService);
    }

    @Test
    void quarantinedTeacher_contextPathStillReturns403() throws Exception {
        UUID userId = UUID.randomUUID();
        mockTeacherRoleCount(userId, 0);

        mockMvc.perform(get("/manabihub/api/v1/teacher/dashboard")
                        .contextPath("/manabihub")
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("role", "TEACHER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void student_teacherDashboardReturns403() throws Exception {
        UUID userId = UUID.randomUUID();
        mockTeacherRoleCount(userId, 0);

        mockMvc.perform(get("/api/v1/teacher/dashboard")
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("role", "STUDENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void student_writingReviewReturns403() throws Exception {
        UUID userId = UUID.randomUUID();
        mockTeacherRoleCount(userId, 0);

        mockMvc.perform(get("/api/v1/teacher/writing-submissions")
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("role", "STUDENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void student_kycEndpointReturns200WithoutTeacherRoleLookup() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherKycService.getStatus(userId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/teacher/kyc/status")
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("role", "STUDENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void student_kycLookalikePathIsNotExemptFromTeacherGate() throws Exception {
        UUID userId = UUID.randomUUID();
        mockTeacherRoleCount(userId, 0);

        mockMvc.perform(get("/api/v1/teacher/kyc-admin")
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("role", "STUDENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherEndpoint_invalidJwtSubjectReturns403WithoutRoleLookup() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/dashboard")
                        .with(jwt().jwt(j -> j.subject("not-a-uuid").claim("role", "TEACHER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void teacherEndpoint_nonJwtAuthenticationReturns403WithoutRoleLookup() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/dashboard")
                        .with(user("teacher").roles("TEACHER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jdbcTemplate);
    }

    private void mockTeacherRoleCount(UUID userId, int count) {
        when(jdbcTemplate.queryForObject(
                COUNT_TEACHER_ROLE_SQL,
                Integer.class,
                userId,
                TEACHER_ROLE_ID
        )).thenReturn(count);
    }
}
