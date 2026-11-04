package com.manabihub.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.audit.service.AccessDenialAuditService;
import com.manabihub.common.constants.MessageCodes;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherEligibilityFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokedTeacherRoleReturns403AndRecordsDenialBeforeMvc() throws Exception {
        UUID userId = UUID.randomUUID();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(userId), any(UUID.class)))
                .thenReturn(0);
        AccessDenialAuditService auditService = mock(AccessDenialAuditService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AccessDenialAuditService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(auditService);
        TeacherEligibilityFilter filter = new TeacherEligibilityFilter(
                jdbcTemplate, new ObjectMapper().findAndRegisterModules(), provider);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/teacher/courses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                .subject(userId.toString()).claim("type", "PUBLIC_USER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(), userId.toString()));

        filter.doFilterInternal(request, response, chain);

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        verify(auditService).record(request, MessageCodes.AUTH_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }
}
