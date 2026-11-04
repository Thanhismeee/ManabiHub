package com.manabihub.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPasswordResetRequestLimiterTest {

    @Mock private DatabaseAuthRateLimiter rateLimiter;

    @Test
    void blockedIpIsRejectedBeforeCreatingAnEmailBucket() {
        when(rateLimiter.consume(
                "admin-password-reset-ip",
                "127.0.0.1",
                "ADMIN_PASSWORD_FORGOT",
                11,
                900,
                900
        )).thenReturn(false);
        AdminPasswordResetRequestLimiter limiter =
                new AdminPasswordResetRequestLimiter(rateLimiter);

        assertFalse(limiter.allow("unknown@example.com", "127.0.0.1"));

        verify(rateLimiter, never()).consume(
                "admin-password-reset-email",
                "unknown@example.com",
                "ADMIN_PASSWORD_FORGOT",
                4,
                900,
                900
        );
    }

    @Test
    void allowedRequestConsumesIpBucketBeforeEmailBucket() {
        when(rateLimiter.consume(
                "admin-password-reset-ip",
                "127.0.0.1",
                "ADMIN_PASSWORD_FORGOT",
                11,
                900,
                900
        )).thenReturn(true);
        when(rateLimiter.consume(
                "admin-password-reset-email",
                "admin@example.com",
                "ADMIN_PASSWORD_FORGOT",
                4,
                900,
                900
        )).thenReturn(true);
        AdminPasswordResetRequestLimiter limiter =
                new AdminPasswordResetRequestLimiter(rateLimiter);

        assertTrue(limiter.allow("admin@example.com", "127.0.0.1"));

        InOrder order = inOrder(rateLimiter);
        order.verify(rateLimiter).consume(
                "admin-password-reset-ip",
                "127.0.0.1",
                "ADMIN_PASSWORD_FORGOT",
                11,
                900,
                900
        );
        order.verify(rateLimiter).consume(
                "admin-password-reset-email",
                "admin@example.com",
                "ADMIN_PASSWORD_FORGOT",
                4,
                900,
                900
        );
    }
}
