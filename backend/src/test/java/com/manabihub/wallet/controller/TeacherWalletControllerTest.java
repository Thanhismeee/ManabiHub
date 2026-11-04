package com.manabihub.wallet.controller;

import com.manabihub.security.DummyFilterConfig;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.manabihub.wallet.dto.response.TeacherWalletResponse;
import com.manabihub.wallet.service.EscrowService;
import com.manabihub.wallet.service.TeacherRevenueService;
import com.manabihub.wallet.service.WalletService;
import com.manabihub.wallet.service.WalletTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherWalletController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class TeacherWalletControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private WalletService walletService;
    @MockBean private EscrowService escrowService;
    @MockBean private WalletTransactionService walletTransactionService;
    @MockBean private TeacherRevenueService teacherRevenueService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    @WithMockUser(username = "f1000000-0000-0000-0000-000000000001", roles = "TEACHER")
    void teacherCanReadWallet() throws Exception {
        UUID userId = UUID.fromString("f1000000-0000-0000-0000-000000000001");
        when(walletService.getTeacherWalletByUserId(userId)).thenReturn(
                TeacherWalletResponse.builder()
                        .availableBalance(new BigDecimal("900000.00"))
                        .pendingBalance(new BigDecimal("100000.00"))
                        .reservedBalance(BigDecimal.ZERO)
                        .build());

        mockMvc.perform(get("/api/v1/teacher/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalance").value(900000.0))
                .andExpect(jsonPath("$.data.pendingBalance").value(100000.0));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotReadTeacherWallet() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/wallet"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(walletService);
    }

    @Test
    void anonymousUserCannotReadTeacherWallet() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/wallet"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(walletService);
    }
}
