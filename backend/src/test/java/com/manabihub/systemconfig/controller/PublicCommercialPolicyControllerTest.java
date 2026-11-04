package com.manabihub.systemconfig.controller;

import com.manabihub.security.DummyFilterConfig;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.manabihub.systemconfig.model.CommercialPolicy;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicCommercialPolicyController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class PublicCommercialPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CommercialPolicyService commercialPolicyService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void anonymousGetReturnsStrictPublicContractInsideApiEnvelope() throws Exception {
        when(commercialPolicyService.getCurrentPolicy()).thenReturn(policy());

        mockMvc.perform(get("/api/v1/public/commercial-policy/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.commissionRate").value(0.2))
                .andExpect(jsonPath("$.data.refundWindowDays").value(7))
                .andExpect(jsonPath("$.data.refundProgressLimitPercent").value(30))
                .andExpect(jsonPath("$.data.escrowHoldingDays").value(14))
                .andExpect(jsonPath("$.data.payoutThreshold").value(100000))
                .andExpect(jsonPath("$.data.withdrawalFee").value(0))
                .andExpect(jsonPath("$.data.kycTargetDaysMin").value(1))
                .andExpect(jsonPath("$.data.kycTargetDaysMax").value(2))
                .andExpect(jsonPath("$.data.policyVersion").value("policy-2026-07-28"))
                .andExpect(jsonPath("$.data.effectiveAt").value("2026-07-28T00:00:00Z"))
                .andExpect(jsonPath("$.data.version").doesNotExist())
                .andExpect(jsonPath("$.data.effectiveDate").doesNotExist())
                .andExpect(jsonPath("$.data.gatewayFee").doesNotExist());
    }

    @Test
    void anonymousPostIsNotPublic() throws Exception {
        mockMvc.perform(post("/api/v1/public/commercial-policy/current"))
                .andExpect(status().isUnauthorized());
    }

    private CommercialPolicy policy() {
        return new CommercialPolicy(
                "VND",
                new BigDecimal("0.20"),
                7,
                30,
                14,
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                1,
                2,
                "policy-2026-07-28",
                Instant.parse("2026-07-28T00:00:00Z"));
    }
}
