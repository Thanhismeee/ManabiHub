package com.manabihub.identity.service;

import org.springframework.stereotype.Component;

@Component
public class AdminPasswordResetRequestLimiter {

    private static final int WINDOW_SECONDS = 15 * 60;
    private static final int BLOCK_SECONDS = 15 * 60;


    /** Requests processed per IP address within one window. */
    private static final int MAX_REQUESTS_PER_IP = 10;
    /** Requests processed per email address within one window. */
    private static final int MAX_REQUESTS_PER_EMAIL = 3;
    // DatabaseAuthRateLimiter.consume() blocks the call whose attempt count
    // reaches the threshold it is given. This limiter runs BEFORE the request
    // is processed, so the threshold must be one above the number of requests
    // we want to process. (AdminLoginProtection consumes AFTER a failed login,
    // so "lock after the Nth failure" is correct there with N as-is.)

    private final DatabaseAuthRateLimiter rateLimiter;

    public AdminPasswordResetRequestLimiter(DatabaseAuthRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public boolean allow(String normalizedEmail, String ipAddress) {
        boolean ipAllowed = rateLimiter.consume(
                "admin-password-reset-ip",
                ipAddress,
                "ADMIN_PASSWORD_FORGOT",
                MAX_REQUESTS_PER_IP + 1,
                WINDOW_SECONDS,
                BLOCK_SECONDS
        );
        if (!ipAllowed) {
            return false;
        }
        return rateLimiter.consume(
                "admin-password-reset-email",
                normalizedEmail,
                "ADMIN_PASSWORD_FORGOT",
                MAX_REQUESTS_PER_EMAIL + 1,
                WINDOW_SECONDS,
                BLOCK_SECONDS
        );
    }
}
