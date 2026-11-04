package com.manabihub.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Rejects stale internal-admin JWTs after an account is disabled or its role is
 * reassigned. Method-level authorization still decides which live role may use
 * an endpoint; this filter only guarantees that the JWT matches database state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalAdminRoleFilter extends OncePerRequestFilter {

    private static final List<String> ADMIN_ROOTS = List.of("/api/v1/admin", "/api/admin");
    private static final List<String> PUBLIC_ADMIN_AUTH_PATHS = List.of(
            "/api/admin/auth/login",
            "/api/admin/auth/setup-password",
            "/api/admin/auth/refresh",
            "/api/admin/auth/logout",
            "/api/admin/auth/password/forgot",
            "/api/admin/auth/password/reset"
    );
    private static final String LIVE_ROLE_SQL = """
            SELECT roles.code,
                   accounts.credential_version AS account_version,
                   sessions.credential_version AS session_version
            FROM internal_admin_accounts accounts
            JOIN internal_admin_roles assignments ON assignments.admin_account_id = accounts.id
            JOIN roles ON roles.id = assignments.role_id
            JOIN internal_admin_sessions sessions ON sessions.admin_account_id = accounts.id
            WHERE accounts.id = ?
              AND sessions.id = ?
              AND accounts.account_status = 'ACTIVE'
              AND sessions.revoked_at IS NULL
              AND sessions.expires_at > CURRENT_TIMESTAMP
              AND sessions.idle_expires_at > CURRENT_TIMESTAMP
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithoutContext(request);
        return PUBLIC_ADMIN_AUTH_PATHS.contains(path)
                || ADMIN_ROOTS.stream().noneMatch(root -> matchesPathOrDescendant(path, root));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            writeStaleSession(request, response);
            return;
        }

        UUID adminId;
        UUID sessionId;
        long tokenCredentialVersion;
        try {
            adminId = UUID.fromString(jwt.getSubject());
            sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
            Number credentialVersion = jwt.getClaim("cv");
            tokenCredentialVersion = credentialVersion.longValue();
        } catch (IllegalArgumentException
                 | NullPointerException
                 | ClassCastException exception) {
            writeStaleSession(request, response);
            return;
        }

        List<LiveAdminSession> liveSessions = jdbcTemplate.query(
                LIVE_ROLE_SQL,
                (resultSet, rowNumber) -> new LiveAdminSession(
                        resultSet.getString("code"),
                        resultSet.getLong("account_version"),
                        resultSet.getLong("session_version")
                ),
                adminId,
                sessionId
        );
        boolean internalAdminToken = "ADMIN_ACCESS".equals(jwt.getClaimAsString("type"))
                && "manabihub-admin".equals(jwt.getClaimAsString("iss"))
                && jwt.getAudience() != null
                && jwt.getAudience().contains("admin-api");
        boolean jwtMatchesLiveState = internalAdminToken
                && liveSessions.size() == 1
                && liveSessions.getFirst().accountVersion() == tokenCredentialVersion
                && liveSessions.getFirst().sessionVersion() == tokenCredentialVersion
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(
                        "ROLE_" + liveSessions.getFirst().role()
                ));

        if (!jwtMatchesLiveState) {
            log.warn(
                    "Admin session rejected for account {} on {} because the JWT role does not match live state.",
                    adminId,
                    request.getRequestURI()
            );
            writeStaleSession(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeStaleSession(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ApiResponse<Void> body = ApiResponse.error(
                MessageCodes.ADMIN_SESSION_STALE,
                "Administrator permissions changed. Sign in again.",
                request.getRequestURI()
        );
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String pathWithoutContext(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }

    private boolean matchesPathOrDescendant(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    record LiveAdminSession(
            String role,
            long accountVersion,
            long sessionVersion
    ) {
    }
}
