package com.manabihub.identity.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AdminRefreshCookieService {

    public static final String DEFAULT_COOKIE_NAME = "mh_admin_refresh";

    private final boolean secure;
    private final String sameSite;

    public AdminRefreshCookieService(
            @Value("${manabihub.admin-auth.cookie-secure:false}")
            boolean secure,
            @Value("${manabihub.admin-auth.cookie-same-site:Lax}")
            String sameSite
    ) {
        this.secure = secure;
        this.sameSite = sameSite;
        if ("None".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalStateException(
                    "SameSite=None admin cookies must also be Secure"
            );
        }
    }

    public void write(
            HttpServletResponse response,
            String rawRefreshToken,
            boolean rememberMe,
            Instant expiresAt
    ) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(DEFAULT_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/admin/auth");
        if (rememberMe) {
            builder.maxAge(Duration.between(Instant.now(), expiresAt));
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(DEFAULT_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/admin/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
