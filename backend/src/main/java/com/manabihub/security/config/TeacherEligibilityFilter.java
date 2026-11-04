package com.manabihub.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.audit.service.AccessDenialAuditService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Database-backed eligibility gate for operational {@code /api/v1/teacher/**}
 * endpoints. Teacher-candidate KYC endpoints are excluded because students use
 * them before the TEACHER role is granted.
 * <p>
 * JWT tokens are stateless and may carry a stale {@code ROLE_TEACHER} claim
 * after quarantine revokes the database role. This filter runs <b>after</b>
 * JWT authentication and checks the live {@code user_roles} table before
 * allowing the request to reach the controller.
 * <p>
 * If the caller no longer holds the TEACHER role in the database, an HTTP 403
 * response is returned immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeacherEligibilityFilter extends OncePerRequestFilter {

    private static final String TEACHER_PATH = "/api/v1/teacher";
    private static final String KYC_PATH = TEACHER_PATH + "/kyc";
    private static final UUID TEACHER_ROLE_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");

    private static final String COUNT_TEACHER_ROLE_SQL =
            "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AccessDenialAuditService> accessDenialAuditServiceProvider;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
        return !matchesPathOrDescendant(path, TEACHER_PATH)
                || matchesPathOrDescendant(path, KYC_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Let Spring Security produce the authentication response for anonymous callers.
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            log.warn("Teacher endpoint access denied: authenticated principal is not a JWT.");
            writeForbiddenResponse(request, response);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            log.warn("Teacher endpoint access denied: JWT subject is not a valid user ID.");
            writeForbiddenResponse(request, response);
            return;
        }

        Integer count = jdbcTemplate.queryForObject(COUNT_TEACHER_ROLE_SQL, Integer.class, userId, TEACHER_ROLE_ID);

        if (count == null || count == 0) {
            log.warn("Teacher endpoint access denied for user {} on {}: live TEACHER role is absent.",
                    userId, request.getRequestURI());

            writeForbiddenResponse(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeForbiddenResponse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AccessDenialAuditService auditService = accessDenialAuditServiceProvider.getIfAvailable();
        if (auditService != null) {
            auditService.record(request, MessageCodes.AUTH_FORBIDDEN);
        }
        ApiResponse<Void> body = ApiResponse.error(
                MessageCodes.AUTH_FORBIDDEN,
                "Teacher access is not available for this account.",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private boolean matchesPathOrDescendant(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }
}
