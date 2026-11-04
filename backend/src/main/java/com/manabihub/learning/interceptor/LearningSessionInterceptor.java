package com.manabihub.learning.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.identity.service.LearningSessionLeaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LearningSessionInterceptor implements HandlerInterceptor {

    private final LearningSessionLeaseService leaseService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return true;
        }

        if (!"PUBLIC_USER".equals(jwt.getClaimAsString("type"))) {
            return true;
        }

        String sidStr = jwt.getClaimAsString("sid");
        if (sidStr == null) {
            return true; // handled by filter
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        UUID sessionId;
        try {
            sessionId = UUID.fromString(sidStr);
        } catch (IllegalArgumentException e) {
            return true;
        }

        if (!leaseService.ownsLease(userId, sessionId)) {
            ApiResponse<Void> body = ApiResponse.error(
                    MessageCodes.ACCOUNT_IN_USE_ELSEWHERE,
                    "Your account is being used to learn on another device.",
                    request.getRequestURI()
            );
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), body);
            return false;
        }

        return true;
    }
}
