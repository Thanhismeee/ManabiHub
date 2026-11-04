package com.manabihub.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.identity.service.PublicUserSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicUserSessionFilterTest {

    @Mock
    private PublicUserSessionService publicUserSessionService;

    private PublicUserSessionFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        filter = new PublicUserSessionFilter(publicUserSessionService, objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withoutAuthentication_continuesChain() throws Exception {
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(200, response.getStatus());
        verifyNoInteractions(publicUserSessionService);
    }

    @Test
    void doFilterInternal_withMockJwt_continuesChain() throws Exception {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("type", "PUBLIC_USER")
                .subject(UUID.randomUUID().toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verifyNoInteractions(publicUserSessionService);
    }

    @Test
    void doFilterInternal_withMissingSidClaim_returnsUnauthorized() throws Exception {
        Jwt jwt = Jwt.withTokenValue("real-token")
                .header("alg", "HS256")
                .claim("type", "PUBLIC_USER")
                .subject(UUID.randomUUID().toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(publicUserSessionService);
    }

    @Test
    void doFilterInternal_withValidSession_continuesChain() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("real-token")
                .header("alg", "HS256")
                .claim("type", "PUBLIC_USER")
                .claim("sid", sessionId.toString())
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

        when(publicUserSessionService.isSessionValid(sessionId, userId)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(publicUserSessionService).isSessionValid(sessionId, userId);
    }

    @Test
    void doFilterInternal_withRevokedSession_returnsUnauthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("real-token")
                .header("alg", "HS256")
                .claim("type", "PUBLIC_USER")
                .claim("sid", sessionId.toString())
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

        when(publicUserSessionService.isSessionValid(sessionId, userId)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(publicUserSessionService).isSessionValid(sessionId, userId);
    }
}
