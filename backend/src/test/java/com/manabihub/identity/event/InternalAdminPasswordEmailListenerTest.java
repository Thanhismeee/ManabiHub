package com.manabihub.identity.event;

import com.manabihub.common.mail.EmailService;
import com.manabihub.identity.service.InternalAdminCredentialDeliveryFailureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalAdminPasswordEmailListenerTest {

    @Mock private EmailService emailService;
    @Mock private InternalAdminCredentialDeliveryFailureService deliveryFailureService;

    @Test
    void failedResetDeliveryRevokesTheUndeliveredToken() {
        InternalAdminPasswordEmailListener listener = listener();
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(emailService)
                .sendEmailSynchronously(anyString(), anyString(), anyString());

        listener.sendResetLink(new InternalAdminPasswordResetIssuedEvent(
                UUID.randomUUID(),
                "finance@example.com",
                "Finance Manager",
                "reset-token",
                Instant.parse("2026-07-30T10:00:00Z")
        ));

        verify(deliveryFailureService).revokePasswordReset("reset-token");
    }

    @Test
    void failedChangedNotificationIsAuditedWithoutUndoingPasswordChange() {
        InternalAdminPasswordEmailListener listener = listener();
        UUID adminId = UUID.randomUUID();
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(emailService)
                .sendEmailSynchronously(anyString(), anyString(), anyString());

        listener.sendPasswordChanged(new InternalAdminPasswordChangedEvent(
                adminId,
                "finance@example.com",
                "Finance Manager",
                Instant.parse("2026-07-30T10:00:00Z")
        ));

        verify(deliveryFailureService)
                .recordPasswordChangedNotificationFailure(adminId);
    }

    private InternalAdminPasswordEmailListener listener() {
        InternalAdminPasswordEmailListener listener =
                new InternalAdminPasswordEmailListener(
                        emailService,
                        deliveryFailureService
                );
        ReflectionTestUtils.setField(
                listener,
                "frontendBaseUrl",
                "https://develop.example.com/"
        );
        return listener;
    }
}
