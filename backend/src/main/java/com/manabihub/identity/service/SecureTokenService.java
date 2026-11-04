package com.manabihub.identity.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class SecureTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        if (!StringUtils.hasText(rawToken) || rawToken.length() > 512) {
            throw new IllegalArgumentException("Token is missing or too long");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public boolean matches(String rawToken, String expectedHash) {
        if (!StringUtils.hasText(rawToken) || !StringUtils.hasText(expectedHash)) {
            return false;
        }
        try {
            return MessageDigest.isEqual(
                    hash(rawToken).getBytes(StandardCharsets.US_ASCII),
                    expectedHash.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
