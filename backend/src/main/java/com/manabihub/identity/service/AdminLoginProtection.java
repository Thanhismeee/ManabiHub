package com.manabihub.identity.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class AdminLoginProtection {

    private static final int WINDOW_SECONDS = 15 * 60;
    private static final int BLOCK_SECONDS = 30 * 60;

    private final DatabaseAuthRateLimiter rateLimiter;

    public AdminLoginProtection(DatabaseAuthRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public void check(String email, String ipAddress) {
        if (rateLimiter.isBlocked("admin-login-email", email)
                || rateLimiter.isBlocked("admin-login-ip", ipAddress)
                || rateLimiter.isBlocked(
                        "admin-login-combination",
                        email + "|" + ipAddress
                )) {
            throw new BusinessException(
                    MessageCodes.MSG_AUTH_008,
                    "Admin login temporarily rate limited"
            );
        }
    }

    public void recordFailure(String email, String ipAddress) {
        rateLimiter.consume(
                "admin-login-email",
                email,
                "ADMIN_LOGIN",
                10,
                WINDOW_SECONDS,
                BLOCK_SECONDS
        );
        rateLimiter.consume(
                "admin-login-ip",
                ipAddress,
                "ADMIN_LOGIN",
                30,
                WINDOW_SECONDS,
                BLOCK_SECONDS
        );
        rateLimiter.consume(
                "admin-login-combination",
                email + "|" + ipAddress,
                "ADMIN_LOGIN",
                5,
                WINDOW_SECONDS,
                BLOCK_SECONDS
        );
    }

    public void reset(String email, String ipAddress) {
        rateLimiter.clear("admin-login-email", email);
        rateLimiter.clear("admin-login-combination", email + "|" + ipAddress);
    }
}
