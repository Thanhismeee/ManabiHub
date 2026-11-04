package com.manabihub.order.controller;

import com.manabihub.common.response.PageResponse;
import com.manabihub.order.dto.response.OrderResponse;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.service.OrderService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, DummyFilterConfig.class})
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;
    @MockBean private PaymentService paymentService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCanReadOwnFilteredPurchaseHistory() throws Exception {
        OrderResponse order = new OrderResponse(
                UUID.randomUUID(),
                "OD202607270001",
                new BigDecimal("150000.00"),
                BigDecimal.ZERO,
                "VND",
                "PAID",
                "COURSE",
                Instant.parse("2026-07-27T00:00:00Z"),
                List.of());
        PageResponse<OrderResponse> page = PageResponse.<OrderResponse>builder()
                .content(List.of(order))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
        when(orderService.getOrdersForCurrentStudent(eq(OrderStatus.PAID), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/orders").param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("ORDER_RETRIEVED"))
                .andExpect(jsonPath("$.data.content[0].orderCode").value("OD202607270001"))
                .andExpect(jsonPath("$.data.content[0].status").value("PAID"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotReadStudentPurchaseHistory() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService);
    }

    @Test
    void anonymousUserCannotReadStudentPurchaseHistory() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCanCancelOwnPendingOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrderForCurrentStudent(orderId)).thenReturn(new OrderResponse(
                orderId,
                "OD202608100001",
                new BigDecimal("150000.00"),
                BigDecimal.ZERO,
                "VND",
                "CANCELLED",
                "COURSE",
                Instant.parse("2026-08-10T00:00:00Z"),
                List.of()));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode").value("ORDER_CANCELLED"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(paymentService).cancelPendingOrder(orderId);
    }
}
