package com.manabihub.audit.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessDenialAuditServiceTest {

    @Mock private SecurityEventRecorder recorder;
    @InjectMocks private AccessDenialAuditService auditService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicUserDenialStoresVerifiedUserIdAndRequestMetadata() {
        UUID userId = UUID.randomUUID();
        authenticate(userId.toString(), "PUBLIC_USER", null);

        auditService.record(request(), "AUTH_FORBIDDEN");

        verify(recorder).recordAccessDenied(eq(userId), eq("ACCESS_DENIED"), eq("ENDPOINT"),
                isNull(), eq(Map.of("path", "/api/v1/teacher/courses", "method", "GET",
                        "messageCode", "AUTH_FORBIDDEN")));
    }

    @Test
    void adminDenialStoresAdminIdAndRoleInsteadOfPublicUserId() {
        UUID adminId = UUID.randomUUID();
        authenticate(adminId.toString(), "ADMIN_ACCESS", "COURSE_MANAGER");

        auditService.record(request(), "MSG-ADM-006");

        verify(recorder).recordAdminAccessDenied(eq(adminId), eq("COURSE_MANAGER"),
                eq("ACCESS_DENIED"), eq("ENDPOINT"), anyMap());
    }

    @Test
    void malformedSubjectStillLeavesAnUnattributedEvent() {
        authenticate("not-a-uuid", "PUBLIC_USER", null);

        auditService.record(request(), "AUTH_FORBIDDEN");

        verify(recorder).recordUnattributedAccessDenied(eq("ACCESS_DENIED"),
                eq("ENDPOINT"), anyMap());
    }

    @Test
    void unknownTokenTypeIsNotAttributedToAUserTableRow() {
        authenticate(UUID.randomUUID().toString(), "UNKNOWN", null);

        auditService.record(request(), "AUTH_FORBIDDEN");

        verify(recorder).recordUnattributedAccessDenied(eq("ACCESS_DENIED"),
                eq("ENDPOINT"), anyMap());
    }

    @Test
    void auditFailureNeverReplacesTheForbiddenResponse() {
        UUID userId = UUID.randomUUID();
        authenticate(userId.toString(), "PUBLIC_USER", null);
        doThrow(new IllegalStateException("audit transaction failed"))
                .when(recorder).recordAccessDenied(eq(userId), eq("ACCESS_DENIED"),
                        eq("ENDPOINT"), isNull(), anyMap());

        assertDoesNotThrow(() -> auditService.record(request(), "AUTH_FORBIDDEN"));
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/teacher/courses");
    }

    private void authenticate(String subject, String type, String role) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("type", type);
        if (role != null) {
            builder.claim("role", role);
        }
        Jwt jwt = builder.build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(), subject));
    }
}
