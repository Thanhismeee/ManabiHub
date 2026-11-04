package com.manabihub.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminRefreshCookieServiceTest {

    @Test
    void productionCookieIsHttpOnlySecureScopedAndPersistentWhenRemembered() {
        AdminRefreshCookieService service =
                new AdminRefreshCookieService(true, "None");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.write(
                response,
                "raw-refresh-token",
                true,
                Instant.now().plusSeconds(3600)
        );

        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=None"));
        assertTrue(cookie.contains("Path=/api/admin/auth"));
        assertTrue(cookie.contains("Max-Age="));
    }

    @Test
    void nonRememberedCookieHasNoPersistentMaxAge() {
        AdminRefreshCookieService service =
                new AdminRefreshCookieService(false, "Lax");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.write(
                response,
                "raw-refresh-token",
                false,
                Instant.now().plusSeconds(3600)
        );

        assertFalse(
                response.getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=")
        );
    }

    @Test
    void sameSiteNoneWithoutSecureFailsFast() {
        assertThrows(
                IllegalStateException.class,
                () -> new AdminRefreshCookieService(false, "None")
        );
    }
}
