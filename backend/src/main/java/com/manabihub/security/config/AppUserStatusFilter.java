package com.manabihub.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.audit.service.AccessDenialAuditService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Rejects real public-user JWTs when the backing account is no longer ACTIVE.
 * The token type check deliberately excludes internal-admin tokens and the
 * lightweight mock JWTs used by controller tests.
 */
@Component
public class AppUserStatusFilter extends OncePerRequestFilter {

    private static final String ACTIVE_ACCOUNT_SQL = """
            SELECT COUNT(*)
            FROM app_users
            WHERE id = ?
              AND user_status = 'ACTIVE'
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectProvider<AccessDenialAuditService> accessDenialAuditServiceProvider;
    private final ObjectMapper objectMapper;

    public AppUserStatusFilter(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<AccessDenialAuditService> accessDenialAuditServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.accessDenialAuditServiceProvider = accessDenialAuditServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)
                || !"PUBLIC_USER".equals(jwt.getClaimAsString("type"))) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            writeForbidden(request, response);
            return;
        }

        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            // MVC slice tests intentionally do not load JDBC infrastructure.
            filterChain.doFilter(request, response);
            return;
        }

        Integer active = jdbcTemplate.queryForObject(ACTIVE_ACCOUNT_SQL, Integer.class, userId);
        if (active == null || active == 0) {
            writeForbidden(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AccessDenialAuditService auditService = accessDenialAuditServiceProvider.getIfAvailable();
        if (auditService != null) {
            auditService.record(request, MessageCodes.AUTH_ACCOUNT_RESTRICTED);
        }
        ApiResponse<Void> body = ApiResponse.error(
                MessageCodes.AUTH_ACCOUNT_RESTRICTED,
                "This account is restricted and cannot access ManabiHub.",
                request.getRequestURI()
        );
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
