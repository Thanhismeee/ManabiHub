package com.manabihub.identity.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPasswordChangeProtectionTest {

    @Mock
    private DatabaseAuthRateLimiter rateLimiter;

    @Test
    void blockedAccountCannotKeepGuessingCurrentPassword() {
        UUID adminId = UUID.randomUUID();
        when(rateLimiter.isBlocked(
                "admin-password-change-account",
                adminId.toString()
        )).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new AdminPasswordChangeProtection(rateLimiter)
                        .check(adminId, "127.0.0.1")
        );

        assertEquals(
                MessageCodes.ADMIN_PASSWORD_CHANGE_RATE_LIMITED,
                exception.getMessageCode()
        );
        assertEquals(429, exception.getHttpStatus().value());
    }

    @Test
    void failureConsumesBothAccountAndIpBuckets() {
        UUID adminId = UUID.randomUUID();
        AdminPasswordChangeProtection protection =
                new AdminPasswordChangeProtection(rateLimiter);

        protection.recordFailure(adminId, "127.0.0.1");

        verify(rateLimiter).consume(
                "admin-password-change-account",
                adminId.toString(),
                "ADMIN_PASSWORD_CHANGE",
                5,
                900,
                1800
        );
        verify(rateLimiter).consume(
                "admin-password-change-ip",
                "127.0.0.1",
                "ADMIN_PASSWORD_CHANGE",
                15,
                900,
                1800
        );
    }
}
