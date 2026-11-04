package com.manabihub.payment.controller;

import com.manabihub.order.repository.OrderRepository;
import com.manabihub.payment.config.VnPayProperties;
import com.manabihub.payment.dto.IpnAckResponse;
import com.manabihub.payment.gateway.PaymentGateway;
import com.manabihub.payment.service.PaymentService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class PaymentControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PaymentService paymentService;
    @MockBean private PaymentGateway paymentGateway;
    @MockBean private OrderRepository orderRepository;
    @MockBean private VnPayProperties vnPayProperties;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void anonymousProviderCallback_RemainsPublic() throws Exception {
        when(paymentService.handleIpn(anyMap()))
                .thenReturn(IpnAckResponse.of("97", "Invalid Checksum"));

        mockMvc.perform(get("/api/v1/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "OD-TEST"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousUser_CannotInvokeDevSimulator() throws Exception {
        mockMvc.perform(post("/api/v1/payments/dev/ipn")
                        .param("orderCode", "OD-TEST")
                        .param("success", "true"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService, paymentGateway, orderRepository);
    }

    @Test
    void anonymousBrowserReturn_CannotConfirmPayment() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay/confirm-return")
                        .param("vnp_TxnRef", "OD-TEST"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService, paymentGateway, orderRepository);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void authenticatedStudent_CanForwardSignedBrowserReturn() throws Exception {
        when(paymentService.handleVnPayReturn(anyMap()))
                .thenReturn(IpnAckResponse.of("00", "Return processed"));

        mockMvc.perform(get("/api/v1/payments/vnpay/confirm-return")
                        .param("vnp_TxnRef", "OD-TEST"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void authenticatedUser_CannotInvokeDisabledDevSimulator() throws Exception {
        when(vnPayProperties.isDevSimulatorEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/v1/payments/dev/ipn")
                        .param("orderCode", "OD-TEST")
                        .param("success", "true"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService, paymentGateway, orderRepository);
    }

    @Test
    void properties_DefaultToDisabledSimulator() {
        VnPayProperties properties = new VnPayProperties();

        org.junit.jupiter.api.Assertions.assertFalse(properties.isDevSimulatorEnabled());
    }
}
