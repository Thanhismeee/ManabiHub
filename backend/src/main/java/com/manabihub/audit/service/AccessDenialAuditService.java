package com.manabihub.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/** Records permission denials from MVC as well as filters that return 403 before MVC. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessDenialAuditService {

    private final SecurityEventRecorder recorder;

    public void record(HttpServletRequest request, String messageCode) {
        Map<String, Object> metadata = Map.of(
                "path", request.getRequestURI(),
                "method", request.getMethod(),
                "messageCode", messageCode);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        try {
            if (principal instanceof Jwt jwt) {
                UUID actorId = parseActorId(jwt.getSubject());
                if (actorId != null) {
                    if ("ADMIN_ACCESS".equals(jwt.getClaimAsString("type"))) {
                        recorder.recordAdminAccessDenied(actorId, jwt.getClaimAsString("role"),
                                "ACCESS_DENIED", "ENDPOINT", metadata);
                    } else if ("PUBLIC_USER".equals(jwt.getClaimAsString("type"))) {
                        recorder.recordAccessDenied(actorId, "ACCESS_DENIED", "ENDPOINT", null, metadata);
                    } else {
                        recorder.recordUnattributedAccessDenied("ACCESS_DENIED", "ENDPOINT", metadata);
                    }
                    return;
                }
            }
            // A malformed or non-JWT principal still produced a 403. Preserve the event
            // without attributing it to an unverified user or admin account.
            recorder.recordUnattributedAccessDenied("ACCESS_DENIED", "ENDPOINT", metadata);
        } catch (RuntimeException ex) {
            // A failed audit transaction must never turn the original 403 into a 500.
            log.error("Could not record access denial on {}", request.getRequestURI(), ex);
        }
    }

    private UUID parseActorId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }
}
