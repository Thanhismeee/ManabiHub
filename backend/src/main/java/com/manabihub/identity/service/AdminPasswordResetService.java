package com.manabihub.identity.service;

import java.util.UUID;

public interface AdminPasswordResetService {

    void request(String email, String ipAddress, String userAgent);

    void reset(
            String rawToken,
            String newPassword,
            String ipAddress,
            String userAgent
    );

    void change(
            UUID adminId,
            String currentPassword,
            String newPassword,
            String ipAddress,
            String userAgent
    );
}
