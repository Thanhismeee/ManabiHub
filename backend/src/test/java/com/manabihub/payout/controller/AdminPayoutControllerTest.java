package com.manabihub.payout.controller;

import com.manabihub.payout.dto.response.PayoutDecisionResponse;
import com.manabihub.payout.enums.PayoutStatus;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.payout.service.PayoutSettlementService;
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
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPayoutController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class AdminPayoutControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PayoutSettlementService payoutSettlementService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    @WithMockUser(roles = "FINANCE_MANAGER")
    void financeManagerCanReadQueue() throws Exception {
        when(payoutSettlementService.getPayoutQueue(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/payouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("COMMON_SUCCESS"));
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminCannotExecuteFinancePayout() throws Exception {
        mockMvc.perform(post("/api/admin/payouts/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCannotReadPayoutQueue() throws Exception {
        mockMvc.perform(get("/api/admin/payouts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FINANCE_MANAGER")
    void approveReturnsCanonicalAdminSuccessCode() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(payoutSettlementService.approvePayout(requestId)).thenReturn(
                PayoutDecisionResponse.builder()
                        .withdrawalRequestId(requestId)
                        .settlementId(UUID.randomUUID())
                        .withdrawalStatus(WithdrawalStatus.EXECUTED)
                        .settlementStatus(PayoutStatus.SUCCEEDED)
                        .reconciliationStatus(ReconciliationStatus.MATCHED)
                        .build()
        );

        mockMvc.perform(post("/api/admin/payouts/" + requestId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("MSG-ADM-004"))
                .andExpect(jsonPath("$.data.withdrawalStatus").value("EXECUTED"));
    }

    @Test
    @WithMockUser(roles = "FINANCE_MANAGER")
    void rejectRequiresNonBlankReason() throws Exception {
        mockMvc.perform(post("/api/admin/payouts/" + UUID.randomUUID() + "/reject")
                        .contentType("application/json")
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value("VALIDATION_FAILED"));
    }
}
