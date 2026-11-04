package com.manabihub.kyc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.kyc.domain.KycRequestStatus;
import com.manabihub.kyc.dto.request.KycReviewRequest;
import com.manabihub.kyc.dto.response.KycRequestResponse;
import com.manabihub.kyc.service.KycService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(AdminKycController.class)
@org.springframework.context.annotation.Import(com.manabihub.security.DummyFilterConfig.class)
@AutoConfigureMockMvc
class AdminKycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KycService kycService;

    private UUID seededKycId;
    private KycRequestResponse mockResponse;

    @BeforeEach
    void setUp() {
        seededKycId = UUID.randomUUID();
        mockResponse = new KycRequestResponse();
        mockResponse.setId(seededKycId);
        mockResponse.setDisplayName("Eleanor Pena");
        mockResponse.setStatus(KycRequestStatus.PENDING);
    }

    @Test
    void testGetPendingKycQueue() throws Exception {
        when(kycService.getPendingKycQueue(any())).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/v1/admin/kyc-requests")
                        .with(jwt().jwt(j -> j.subject("c0000000-0000-0000-0000-000000000002"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.messageCode", is("COMMON_SUCCESS")))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].displayName", is("Eleanor Pena")));
    }

    @Test
    void testGetKycDetail() throws Exception {
        when(kycService.getKycDetail(eq(seededKycId), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/admin/kyc-requests/" + seededKycId)
                        .with(jwt().jwt(j -> j.subject("c0000000-0000-0000-0000-000000000002"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.displayName", is("Eleanor Pena")))
                .andExpect(jsonPath("$.data.status", is("PENDING")));
    }

    @Test
    void testApproveKyc() throws Exception {
        KycReviewRequest reviewRequest = KycReviewRequest.builder()
                .status(KycRequestStatus.APPROVED)
                .decisionNote("Looks good")
                .build();

        KycRequestResponse approvedResponse = new KycRequestResponse();
        approvedResponse.setId(seededKycId);
        approvedResponse.setStatus(KycRequestStatus.APPROVED);

        when(kycService.reviewKyc(eq(seededKycId), any(KycReviewRequest.class), any()))
                .thenReturn(approvedResponse);

        mockMvc.perform(post("/api/v1/admin/kyc-requests/" + seededKycId + "/review")
                        .with(jwt().jwt(j -> j.subject("c0000000-0000-0000-0000-000000000002")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.messageCode", is("ADMIN_ACTION_SUCCESS")))
                .andExpect(jsonPath("$.data.status", is("APPROVED")));
    }

    @Test
    void testRejectKycWithoutReasonFails() throws Exception {
        KycReviewRequest reviewRequest = KycReviewRequest.builder()
                .status(KycRequestStatus.REJECTED)
                .decisionNote("") // Empty reason
                .build();

        when(kycService.reviewKyc(eq(seededKycId), any(KycReviewRequest.class), any()))
                .thenThrow(new com.manabihub.common.exception.BusinessException(
                        "VALIDATION_FAILED",
                        "Decision reason is required for rejection or correction request",
                        org.springframework.http.HttpStatus.BAD_REQUEST
                ));

        mockMvc.perform(post("/api/v1/admin/kyc-requests/" + seededKycId + "/review")
                        .with(jwt().jwt(j -> j.subject("c0000000-0000-0000-0000-000000000002")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isBadRequest());
    }
}
