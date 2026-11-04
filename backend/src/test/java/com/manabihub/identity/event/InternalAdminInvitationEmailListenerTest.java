package com.manabihub.identity.event;

import com.manabihub.common.mail.EmailService;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.service.InternalAdminCredentialDeliveryFailureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalAdminInvitationEmailListenerTest {

    @Mock private EmailService emailService;
    @Mock private InternalAdminCredentialDeliveryFailureService deliveryFailureService;

    @Test
    void emailContainsFragmentSetupLinkAndNeverContainsPassword() {
        InternalAdminInvitationEmailListener listener = listener();

        listener.sendInvitation(event());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailSynchronously(
                eq("manager@example.com"),
                eq("Thiết lập tài khoản quản trị ManabiHub"),
                bodyCaptor.capture()
        );
        String body = bodyCaptor.getValue();
        assertTrue(body.contains(
                "https://develop.example.com/admin/setup-password#token=raw-token-value"
        ));
        assertTrue(body.contains("Course &lt;Manager&gt;"));
        assertFalse(body.toLowerCase().contains("mật khẩu tạm"));
    }

    @Test
    void failedDeliveryRevokesTheUndeliveredInvitationToken() {
        InternalAdminInvitationEmailListener listener = listener();
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(emailService)
                .sendEmailSynchronously(anyString(), anyString(), anyString());

        listener.sendInvitation(event());

        verify(deliveryFailureService).revokeInvitation("raw-token-value");
    }

    private InternalAdminInvitationEmailListener listener() {
        InternalAdminInvitationEmailListener listener =
                new InternalAdminInvitationEmailListener(
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

    private InternalAdminInvitationIssuedEvent event() {
        return new InternalAdminInvitationIssuedEvent(
                "manager@example.com",
                "Course <Manager>",
                RoleCode.COURSE_MANAGER,
                "raw-token-value",
                Instant.parse("2026-07-30T10:00:00Z")
        );
    }
}
