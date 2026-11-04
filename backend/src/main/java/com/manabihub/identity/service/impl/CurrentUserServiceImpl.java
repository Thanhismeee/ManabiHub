package com.manabihub.identity.service.impl;

import com.manabihub.identity.service.CurrentUserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CurrentUserServiceImpl implements CurrentUserService {

    @Override
    public UUID getCurrentUserId() {
        return getCurrentUserIdOptional().orElseThrow(() ->
                new AuthenticationCredentialsNotFoundException(
                        "A valid authenticated user UUID is required"
                ));
    }

    @Override
    public Optional<UUID> getCurrentUserIdOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            try {
                return Optional.of(UUID.fromString(jwtAuth.getName()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        try {
            return Optional.of(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String targetRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(targetRole));
    }
}
