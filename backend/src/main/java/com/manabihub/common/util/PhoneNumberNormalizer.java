package com.manabihub.common.util;

/**
 * Utility class for normalising Vietnamese phone numbers before they are persisted.
 * Profile forms accept both {@code 0XXXXXXXXX} and {@code +84XXXXXXXXX}, but only the
 * local form is stored so that a number always has a single representation in
 * {@code app_users.phone_number}. A blank value means the user cleared the optional
 * field and is stored as {@code null}.
 */
public class PhoneNumberNormalizer {

    private static final String VN_COUNTRY_CODE = "+84";

    private static final String VN_TRUNK_PREFIX = "0";

    public static String normalize(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        String trimmed = phoneNumber.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith(VN_COUNTRY_CODE)) {
            return VN_TRUNK_PREFIX + trimmed.substring(VN_COUNTRY_CODE.length());
        }

        return trimmed;
    }

    /** Converts the canonical Vietnamese local form to Firebase's E.164 form. */
    public static String toE164(String phoneNumber) {
        String normalized = normalize(phoneNumber);
        if (normalized == null) {
            return null;
        }
        if (normalized.matches("0\\d{9}")) {
            return VN_COUNTRY_CODE + normalized.substring(1);
        }
        return normalized;
    }
}
