package com.manabihub.identity.service;

import java.time.Instant;

public record AdminSessionBundle(
        String accessToken,
        String refreshToken,
        String csrfToken,
        boolean remembered,
        Instant refreshExpiresAt
) {
}
