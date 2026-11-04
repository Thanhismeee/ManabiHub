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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppUserStatusFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activePublicUserContinuesRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(userId)))
                .thenReturn(1);
        AppUserStatusFilter filter = filterWith(jdbcTemplate);
        FilterChain chain = mock(FilterChain.class);
        authenticate(userId, "PUBLIC_USER");

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                chain
        );

        verify(chain).doFilter(any(), any());
    }

    @Test
    void lockedPublicUserIsRejectedEvenWithAnExistingJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(userId)))
                .thenReturn(0);
        AccessDenialAuditService auditService = mock(AccessDenialAuditService.class);
        AppUserStatusFilter filter = filterWith(jdbcTemplate, auditService);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        authenticate(userId, "PUBLIC_USER");

        filter.doFilterInternal(new MockHttpServletRequest(), response, chain);

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        verify(chain, never()).doFilter(any(), any());
        verify(auditService).record(any(), eq(MessageCodes.AUTH_ACCOUNT_RESTRICTED));
    }

    @Test
    void internalAdminTokenIsNotLookedUpInPublicUserTable() throws Exception {
        UUID adminId = UUID.randomUUID();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AppUserStatusFilter filter = filterWith(jdbcTemplate);
        FilterChain chain = mock(FilterChain.class);
        authenticate(adminId, "INTERNAL_ADMIN");

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                chain
        );

        verify(jdbcTemplate, never())
                .queryForObject(any(String.class), eq(Integer.class), any(UUID.class));
        verify(chain).doFilter(any(), any());
    }

    private AppUserStatusFilter filterWith(JdbcTemplate jdbcTemplate) {
        return filterWith(jdbcTemplate, null);
    }

    private AppUserStatusFilter filterWith(JdbcTemplate jdbcTemplate, AccessDenialAuditService auditService) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        @SuppressWarnings("unchecked")
        ObjectProvider<AccessDenialAuditService> auditProvider = mock(ObjectProvider.class);
        if (auditService != null) {
            when(auditProvider.getIfAvailable()).thenReturn(auditService);
        }
        return new AppUserStatusFilter(
                provider,
                auditProvider,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    private void authenticate(UUID subject, String tokenType) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject.toString())
                .claim("type", tokenType)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(), subject.toString())
        );
    }
}
