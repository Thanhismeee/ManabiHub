package com.manabihub.identity.service;

import com.manabihub.identity.entity.PublicUserSession;

import java.util.UUID;

public interface PublicUserSessionService {
    
    /**
     * Attempts to create a session for the user with the given device key.
     * @return the created session
     * @throws com.manabihub.common.exception.BusinessException if device limit reached.
     */
    PublicUserSession createSession(UUID userId, String deviceKey, String userAgent, String displayName);
    
    /**
     * Validates if a session exists, belongs to the user, and is not revoked or expired.
     */
    boolean isSessionValid(UUID sessionId, UUID userId);

    void revokeDevice(UUID userId, UUID deviceId);

    void revokeSession(UUID sessionId);
}
