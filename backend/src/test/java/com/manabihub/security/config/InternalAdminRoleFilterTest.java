package com.manabihub.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalAdminRoleFilterTest {

    private JdbcTemplate jdbcTemplate;
    private InternalAdminRoleFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        filter = new InternalAdminRoleFilter(
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules()
        );
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void matchingLiveRoleAndCredentialVersionAllowsAdminRequest() throws Exception {
        AuthenticationIds ids = authenticate("SYSTEM_ADMIN", 3);
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq(ids.adminId()),
                eq(ids.sessionId())
        )).thenReturn(List.of(
                new InternalAdminRoleFilter.LiveAdminSession(
                        "SYSTEM_ADMIN",
                        3,
                        3
                )
        ));
        MockHttpServletRequest request = adminRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void changedRoleReturns401SoFrontendClearsAdminSession() throws Exception {
        AuthenticationIds ids = authenticate("SYSTEM_ADMIN", 3);
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq(ids.adminId()),
                eq(ids.sessionId())
        )).thenReturn(List.of(
                new InternalAdminRoleFilter.LiveAdminSession(
                        "COURSE_MANAGER",
                        3,
                        3
                )
        ));
        MockHttpServletRequest request = adminRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertStaleSession(response);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void changedCredentialVersionReturns401() throws Exception {
        AuthenticationIds ids = authenticate("SYSTEM_ADMIN", 3);
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq(ids.adminId()),
                eq(ids.sessionId())
        )).thenReturn(List.of(
                new InternalAdminRoleFilter.LiveAdminSession(
                        "SYSTEM_ADMIN",
                        4,
                        3
                )
        ));
        MockHttpServletRequest request = adminRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertStaleSession(response);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void malformedAdminTokenReturns401WithoutDatabaseLookup() throws Exception {
        UUID adminId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(adminId.toString())
                .claim("role", "SYSTEM_ADMIN")
                .claim("sid", UUID.randomUUID().toString())
                .claim("cv", "not-a-number")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))
                )
        );
        MockHttpServletRequest request = adminRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertStaleSession(response);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void publicAdminLoginDoesNotReadLiveRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/admin/auth/login"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jdbcTemplate);
    }

    private AuthenticationIds authenticate(String role, long credentialVersion) {
        UUID adminId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("manabihub-admin")
                .audience(List.of("admin-api"))
                .subject(adminId.toString())
                .claim("role", role)
                .claim("type", "ADMIN_ACCESS")
                .claim("sid", sessionId.toString())
                .claim("cv", credentialVersion)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
        return new AuthenticationIds(adminId, sessionId);
    }

    private MockHttpServletRequest adminRequest() {
        return new MockHttpServletRequest(
                "GET",
                "/api/v1/admin/system-settings"
        );
    }

    private void assertStaleSession(MockHttpServletResponse response)
            throws Exception {
        assertEquals(401, response.getStatus());
        assertEquals(
                MessageCodes.ADMIN_SESSION_STALE,
                new ObjectMapper().readTree(response.getContentAsByteArray())
                        .get("messageCode")
                        .asText()
        );
    }

    private record AuthenticationIds(UUID adminId, UUID sessionId) {
    }
}
