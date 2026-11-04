package com.manabihub.identity.event;

import com.manabihub.identity.service.InternalAdminSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalAdminSessionInvalidationListenerTest {

    @Mock
    private InternalAdminSessionService sessionService;

    @Test
    void deferredCleanupRevokesAllSessionsForTheChangedAccount() {
        UUID adminId = UUID.randomUUID();
        InternalAdminSessionInvalidationListener listener =
                new InternalAdminSessionInvalidationListener(sessionService);

        listener.revokeSessions(new InternalAdminSessionsInvalidatedEvent(
                adminId,
                "PASSWORD_CHANGED",
                Instant.parse("2026-07-29T10:00:00Z")
        ));

        verify(sessionService).revokeAll(adminId);
    }
}
