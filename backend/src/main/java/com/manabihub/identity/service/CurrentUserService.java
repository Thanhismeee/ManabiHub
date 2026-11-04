package com.manabihub.identity.service;

import java.util.UUID;

/**
 * Provides information about the currently authenticated user.
 * <p>
 * Identity is resolved from the active Spring Security authentication.
 */
public interface CurrentUserService {

    /**
     * Returns the current authenticated user's ID.
     *
     * @return current user's UUID
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
     *         when no valid authenticated UUID is available
     */
    UUID getCurrentUserId();

    /**
     * Returns the current authenticated user's ID, if any.
     * Use this for public endpoints where user might be unauthenticated.
     */
    java.util.Optional<UUID> getCurrentUserIdOptional();

    /**
     * Checks if the current authenticated user has the specified role.
     *
     * @param role the role to check (e.g., "ROLE_ADMIN" or "ADMIN")
     * @return true if the user has the role, false otherwise
     */
    boolean hasRole(String role);
}
