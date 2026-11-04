package com.manabihub.identity.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class InternalAdminPasswordPolicy {

    public static final int MIN_PASSWORD_BYTES = 12;
    public static final int MAX_PASSWORD_BYTES = 72;

    public void validate(String password) {
        int byteLength = password == null
                ? 0
                : password.getBytes(StandardCharsets.UTF_8).length;
        boolean meetsComposition = password != null
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9].*")
                && password.chars().noneMatch(Character::isWhitespace);

        if (byteLength < MIN_PASSWORD_BYTES
                || byteLength > MAX_PASSWORD_BYTES
                || !meetsComposition) {
            throw new BusinessException(
                    MessageCodes.INTERNAL_ADMIN_PASSWORD_INVALID,
                    "Password must be 12-72 UTF-8 bytes and contain uppercase, lowercase, "
                            + "digit, and special characters without whitespace"
            );
        }
    }
}
