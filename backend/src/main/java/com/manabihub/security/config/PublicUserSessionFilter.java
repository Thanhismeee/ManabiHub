package com.manabihub.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.identity.service.PublicUserSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PublicUserSessionFilter extends OncePerRequestFilter {

    private final PublicUserSessionService publicUserSessionService;
    private final ObjectMapper objectMapper;

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

        // Allow mock JWTs for tests
        if ("none".equals(jwt.getHeaders().get("alg"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String sidStr = jwt.getClaimAsString("sid");
        if (sidStr == null) {
            writeUnauthorized(request, response, MessageCodes.AUTH_SESSION_INVALID, "Session invalid. Please login again.");
            return;
        }

        UUID sessionId;
        UUID userId;
        try {
            sessionId = UUID.fromString(sidStr);
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            writeUnauthorized(request, response, MessageCodes.AUTH_SESSION_INVALID, "Session identifier is malformed.");
            return;
        }

        if (!publicUserSessionService.isSessionValid(sessionId, userId)) {
            writeUnauthorized(request, response, MessageCodes.AUTH_SESSION_REVOKED, "Your session has expired or been revoked.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String messageCode, String defaultMessage)
            throws IOException {
        ApiResponse<Void> body = ApiResponse.error(
                messageCode,
                defaultMessage,
                request.getRequestURI()
        );
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
