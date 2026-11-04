package com.manabihub.identity.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdminPasswordChangeProtection {

    private static final int WINDOW_SECONDS = 15 * 60;
    private static final int BLOCK_SECONDS = 30 * 60;

    private final DatabaseAuthRateLimiter rateLimiter;

    public void check(UUID adminId, String ipAddress) {
        if (rateLimiter.isBlocked("admin-password-change-account", adminId.toString())
                || rateLimiter.isBlocked("admin-password-change-ip", ipAddress)) {
            throw new BusinessException(
                    MessageCodes.ADMIN_PASSWORD_CHANGE_RATE_LIMITED,
                    "Password change is temporarily rate limited",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    public void recordFailure(UUID adminId, String ipAddress) {
        rateLimiter.consume(
                "admin-password-change-account",
                adminId.toString(),
                "ADMIN_PASSWORD_CHANGE",
                5,
                WINDOW_SECONDS,
                BLOCK_SECONDS
        );
        rateLimiter.consume(
                "admin-password-change-ip",
                ipAddress,
                "ADMIN_PASSWORD_CHANGE",
                15,
                WINDOW_SECONDS,
                BLOCK_SECONDS
        );
    }

    public void reset(UUID adminId) {
        rateLimiter.clear(
                "admin-password-change-account",
                adminId.toString()
        );
    }
}
