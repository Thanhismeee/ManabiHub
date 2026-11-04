package com.manabihub.identity.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserServiceImplTest {

    private final CurrentUserServiceImpl currentUserService = new CurrentUserServiceImpl();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserIdReturnsAuthenticatedJwtSubject() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        ));

        assertEquals(userId, currentUserService.getCurrentUserId());
    }

    @Test
    void getCurrentUserIdRejectsAnonymousAccessInsteadOfUsingDemoIdentity() {
        assertTrue(currentUserService.getCurrentUserIdOptional().isEmpty());
        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                currentUserService::getCurrentUserId
        );
    }

    @Test
    void getCurrentUserIdRejectsNonUuidSubject() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("not-a-user-uuid")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        ));

        assertTrue(currentUserService.getCurrentUserIdOptional().isEmpty());
        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                currentUserService::getCurrentUserId
        );
    }
}
