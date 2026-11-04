package com.manabihub.identity.event;

import com.manabihub.identity.service.InternalAdminSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalAdminSessionInvalidationListener {

    private final InternalAdminSessionService sessionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void revokeSessions(InternalAdminSessionsInvalidatedEvent event) {
        try {
            sessionService.revokeAll(event.adminAccountId());
        } catch (RuntimeException exception) {
            // Credential-version validation already blocks stale JWTs and refreshes.
            log.error(
                    "Deferred internal administrator session cleanup failed for account {}",
                    event.adminAccountId(),
                    exception
            );
        }
    }
}
