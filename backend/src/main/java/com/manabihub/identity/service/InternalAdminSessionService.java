package com.manabihub.identity.service;

import com.manabihub.identity.entity.InternalAdminAccount;

import java.util.UUID;

public interface InternalAdminSessionService {

    AdminSessionBundle create(
            InternalAdminAccount account,
            boolean rememberMe,
            String userAgent
    );

    AdminSessionBundle refresh(
            String refreshToken,
            String csrfToken,
            String userAgent
    );

    void logout(String refreshToken, String csrfToken, String ipAddress, String userAgent);

    void revokeAll(UUID adminAccountId);
}
